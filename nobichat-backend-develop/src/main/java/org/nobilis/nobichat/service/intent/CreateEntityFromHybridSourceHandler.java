package org.nobilis.nobichat.service.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.repository.TemplateRepository;
import org.nobilis.nobichat.service.DynamicEntityQueryService;
import org.nobilis.nobichat.service.DynamicViewGeneratorService;
import org.nobilis.nobichat.service.FileContentExtractorService;
import org.nobilis.nobichat.service.IntentHandler;
import org.nobilis.nobichat.service.LlmPromptService;
import org.nobilis.nobichat.service.OntologyService;
import org.nobilis.nobichat.service.WebPageContentExtractorService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Обработчик намерения "CREATE_ENTITY_FROM_HYBRID_SOURCE".
 * Создает сущность на основе данных, извлеченных одновременно из приложенного файла и указанного URL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateEntityFromHybridSourceHandler implements IntentHandler {

    private final FileContentExtractorService fileContentExtractorService;
    private final WebPageContentExtractorService webPageContentExtractorService;
    private final LlmPromptService llmPromptService; // Используется для формирования промптов и очистки ответов LLM
    private final ObjectMapper objectMapper;
    private final DynamicEntityQueryService dynamicEntityQueryService;
    private final OntologyService ontologyService;
    private final TemplateRepository templateRepository;
    private final DynamicViewGeneratorService dynamicViewGeneratorService; // Для генерации FormView

    private static final long MAX_TEXT_LENGTH_FOR_LLM = 50000;

    @Override
    public String getIntentType() {
        return "CREATE_ENTITY_FROM_HYBRID_SOURCE";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatContext context) {
        boolean hasAttachments = context.getAttachments() != null && !context.getAttachments().isEmpty();
        String sourceUrl = intent.getSourceUrl();
        String entityName = intent.getEntity();
        // Получаем текущую UI-схему сессии для возврата в случае ошибки
        JsonNode currentSchema = context.getCurrentUiMessage() != null && context.getCurrentUiMessage().getTemplate() != null
                ? context.getCurrentUiMessage().getTemplate().getSchema() : null;

        // Проверки на наличие необходимых данных
        if (!hasAttachments || !StringUtils.hasText(sourceUrl)) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Для этого действия необходимо приложить файл и указать ссылку в одном сообщении.", null);
        }
        if (context.getAttachments().size() > 1) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Для создания сущности можно приложить только один файл.", null);
        }
        if (entityName == null || !ontologyService.entityExists(entityName)) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Не удалось определить, какую именно сущность вы хотите создать.", null);
        }

        MultipartFile document = context.getAttachments().get(0);

        try {
            log.info("Начало гибридного извлечения: файл '{}' и URL '{}'", document.getOriginalFilename(), sourceUrl);
            String textFromDocument = fileContentExtractorService.extractTextFromWord(document);
            String textFromUrl = webPageContentExtractorService.extractTextFromUrl(sourceUrl);

            // Объединение текстов из разных источников для отправки в LLM
            String combinedText = String.format(
                    "[КОНТЕКСТ ИЗ ФАЙЛА: %s]\n\n%s\n\n[КОНТЕКСТ С ВЕБ-СТРАНИЦЫ: %s]\n\n%s",
                    document.getOriginalFilename(), textFromDocument, sourceUrl, textFromUrl
            );

            // Обрезка текста, если он слишком длинный для LLM
            if (combinedText.length() > MAX_TEXT_LENGTH_FOR_LLM) {
                log.warn("Объединенный текст слишком длинный ({} символов), будет обрезан до {}", combinedText.length(), MAX_TEXT_LENGTH_FOR_LLM);
                combinedText = combinedText.substring(0, (int) MAX_TEXT_LENGTH_FOR_LLM);
            }

            // Формирование промпта для извлечения данных и отправка в LLM
            String dataExtractionPrompt = llmPromptService.buildDataExtractionPrompt(entityName, combinedText);
            LLMResponseDto llmResponse = llmPromptService.sendPromptToLlm(dataExtractionPrompt, null, true, context.getChatMessage().getId());

            // Очистка и парсинг JSON-ответа от LLM
            String cleanedJson = llmPromptService.sanitizeLlmJsonResponse(llmResponse.getContent());
            Map<String, Object> extractedData = objectMapper.readValue(cleanedJson, new TypeReference<>() {});

            // Создание новой сущности в системе
            Map<String, Object> createdEntity = dynamicEntityQueryService.createEntity(entityName, extractedData);
            UUID newEntityId = UUID.fromString(String.valueOf(createdEntity.get("id")));
            log.info("Создана новая сущность '{}' с ID: {} на основе гибридного источника.", entityName, newEntityId);

            // Генерация FormView для созданной сущности
            JsonNode formViewSchema = dynamicViewGeneratorService.generateFormView(entityName, newEntityId);
            String successMessage = String.format(
                    "Отлично! %s '%s' создан(а) на основе данных из файла и со страницы. Открываю карточку.",
                    ontologyService.getEntityMetaData(entityName).getUserFriendlyName(),
                    createdEntity.getOrDefault("name", "Новая запись")
            );

            // Сохранение сгенерированного шаблона и привязка его к сообщению чата
            Template generatedTemplate = Template.builder()
                    .schema(formViewSchema)
                    .mode(context.getChatMode())
                    .build();
            templateRepository.save(generatedTemplate);
            context.getChatMessage().setTemplate(generatedTemplate);

            return new ChatResponseDto(context.getSession().getId(), formViewSchema, successMessage, null);

        } catch (Exception e) {
            log.error("Ошибка при создании сущности '{}' из гибридного источника (файл + URL): {}", entityName, e.getMessage(), e);
            String errorMessage = "К сожалению, не удалось обработать ваш запрос. Пожалуйста, проверьте корректность файла и ссылки и попробуйте еще раз.";
            // В случае ошибки, сбрасываем шаблон для текущего сообщения
            context.getChatMessage().setTemplate(null);
            return new ChatResponseDto(context.getSession().getId(), null, errorMessage, null);
        }
    }
}