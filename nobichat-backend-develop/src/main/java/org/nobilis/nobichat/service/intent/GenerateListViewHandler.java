package org.nobilis.nobichat.service.intent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.repository.TemplateRepository;
import org.nobilis.nobichat.service.DynamicEntityQueryService;
import org.nobilis.nobichat.service.DynamicViewGeneratorService;
import org.nobilis.nobichat.service.HashService;
import org.nobilis.nobichat.service.IntentHandler;
import org.nobilis.nobichat.service.OntologyService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Обработчик намерения "GENERATE_LIST_VIEW".
 * Отвечает за генерацию и возврат ListView (списка сущностей)
 * или FormView (карточки одной сущности), если запрос специфичен.
 * Поддерживает кэширование и режимы STRICT/SOFT.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateListViewHandler implements IntentHandler {

    private final DynamicViewGeneratorService dynamicViewGeneratorService;
    private final TemplateRepository templateRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final HashService hashService;
    private final DynamicEntityQueryService dynamicEntityQueryService;
    private final OntologyService ontologyService;
    private final Random random = new Random(); // Инициализация Random здесь

    @Override
    public String getIntentType() {
        return "GENERATE_LIST_VIEW";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatContext context) {
        String userQuery = context.getRequest().getMessage();
        Boolean useCache = context.getRequest().getUseCache() != null ? context.getRequest().getUseCache() : false;
        String entityName = intent.getEntity();
        ChatMode mode = context.getChatMode();

        // Проверка, определена ли сущность и существует ли она в онтологии
        if (entityName == null || !ontologyService.entityExists(entityName)) {
            return new ChatResponseDto(context.getSession().getId(),
                    context.getCurrentUiMessage() != null && context.getCurrentUiMessage().getTemplate() != null
                            ? context.getCurrentUiMessage().getTemplate().getSchema() : null,
                    "Не удалось определить, для какой сущности строить интерфейс.",
                    null);
        }

        JsonNode schema = null;
        String responseTextMessage = null;
        Template generatedTemplate = null;

        // Попытка найти одну сущность, если запрос пользователя достаточно специфичен (есть query)
        // и может быть сопоставлен с одной записью
        Optional<UUID> singleEntityIdOpt = dynamicEntityQueryService.findSingleEntityId(entityName, intent.getQuery());

        if (singleEntityIdOpt.isPresent()) {
            // Если найдена одна сущность, генерируем FormView для неё
            log.info("Найдена одна сущность, генерируется FormView в режиме {}", mode);
            schema = dynamicViewGeneratorService.generateFormView(entityName, singleEntityIdOpt.get());
            responseTextMessage = generatePositiveResponseMessageForSingleEntity();

            generatedTemplate = Template.builder().schema(schema).mode(mode).build();
            templateRepository.save(generatedTemplate);

        } else {
            // Если одна сущность не найдена, генерируем ListView
            String requestHash = hashService.generateSha256Hash(userQuery + "_" + mode.name());

            // Попытка использовать кэш, если разрешено и запись существует
            if (useCache) {
                String cachedTemplateIdStr = redisTemplate.opsForValue().get(requestHash);
                if (cachedTemplateIdStr != null) {
                    Optional<Template> cachedTemplateOpt = templateRepository.findById(UUID.fromString(cachedTemplateIdStr));
                    if (cachedTemplateOpt.isPresent()) {
                        generatedTemplate = cachedTemplateOpt.get();
                        schema = generatedTemplate.getSchema();
                        responseTextMessage = generatePositiveResponseMessage();
                        log.info("Кэш-попадание (режим: {}) для хеша: {}. Используется шаблон ID: {}", mode, requestHash, generatedTemplate.getId());
                    } else {
                        // Запись в кэше есть, но шаблон в БД отсутствует - удаляем из кэша
                        redisTemplate.delete(requestHash);
                        log.warn("Кэшированный Template ID {} (режим: {}) не найден. Запись Redis удалена.", cachedTemplateIdStr, mode);
                    }
                }
            }

            // Если шаблон не был получен из кэша, генерируем его заново
            if (schema == null) {
                log.info("Кэш-промах (режим: {}) для хеша: {}. Генерируется новый ListView.", mode, requestHash);

                // Дефолтные параметры пагинации и сортировки для генерации
                Pageable defaultPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));

                if (mode == ChatMode.STRICT) {
                    schema = dynamicViewGeneratorService.generateListViewStrict(entityName, intent.getQuery(), defaultPageable);
                } else { // SOFT режим
                    schema = dynamicViewGeneratorService.generateListViewSoft(entityName, userQuery, intent.getQuery(), context.getChatMessage(), defaultPageable);
                }

                if (schema != null) {
                    // Если схема успешно сгенерирована, сохраняем её и кэшируем
                    responseTextMessage = generatePositiveResponseMessage();
                    generatedTemplate = Template.builder().schema(schema).mode(mode).build();
                    templateRepository.save(generatedTemplate);
                    redisTemplate.opsForValue().set(requestHash, generatedTemplate.getId().toString(), 24, TimeUnit.HOURS);
                    log.info("Сгенерирован, сохранен (ID: {}) и закэширован новый ListView шаблон (режим: {})", generatedTemplate.getId(), mode);
                } else {
                    // Если генерация схемы не удалась
                    responseTextMessage = generateNotFoundResponseMessage();
                }
            }
        }

        // Устанавливаем сгенерированный шаблон в текущее обрабатываемое сообщение
        context.getChatMessage().setTemplate(generatedTemplate);

        // Возвращаем ChatResponseDto
        return new ChatResponseDto(context.getSession().getId(), schema, responseTextMessage, null);
    }

    // Вспомогательные методы для генерации сообщений
    private String generatePositiveResponseMessage() {
        List<String> messages = List.of(
                "Конечно, открываю запрошенную форму.",
                "Один момент, загружаю запрошенную форму...",
                "Пожалуйста, готово!",
                "Вот запрошенная форма, как вы и просили."
        );
        return messages.get(random.nextInt(messages.size()));
    }

    private String generatePositiveResponseMessageForSingleEntity() {
        List<String> messages = List.of(
                "Нашел то, что вы искали. Открываю карточку.",
                "По вашему запросу найдена одна запись. Показываю детали.",
                "Готово! Вот информация по найденной сущности."
        );
        return messages.get(random.nextInt(messages.size()));
    }

    private String generateNotFoundResponseMessage() {
        List<String> messages = List.of(
                "Извините, я не совсем понял, что вы хотите открыть. Попробуйте переформулировать запрос.",
                "Хм, я не смог найти подходящий интерфейс для вашего запроса. Можете уточнить?",
                "К сожалению, я не могу обработать этот запрос. Пожалуйста, попробуйте другую команду."
        );
        return messages.get(random.nextInt(messages.size()));
    }
}