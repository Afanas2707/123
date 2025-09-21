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
import org.nobilis.nobichat.service.LlmPromptService;
import org.nobilis.nobichat.service.OntologyService;
import org.nobilis.nobichat.service.WebPageContentExtractorService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Обработчик намерения "CREATE_ENTITY_FROM_URL".
 * Создает сущность на основе данных, извлеченных из указанного URL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateEntityFromUrlHandler implements IntentHandler {

    private final WebPageContentExtractorService webPageContentExtractorService;
    private final LlmPromptService llmPromptService;
    private final ObjectMapper objectMapper;
    private final DynamicEntityQueryService dynamicEntityQueryService;
    private final OntologyService ontologyService;
    private final TemplateRepository templateRepository;
    private final DynamicViewGeneratorService dynamicViewGeneratorService;

    private static final long MAX_TEXT_LENGTH_FOR_LLM = 30000;

    @Override
    public String getIntentType() {
        return "CREATE_ENTITY_FROM_URL";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatContext context) {
        String sourceUrl = intent.getSourceUrl();
        String entityName = intent.getEntity();
        JsonNode currentSchema = context.getCurrentUiMessage() != null && context.getCurrentUiMessage().getTemplate() != null
                ? context.getCurrentUiMessage().getTemplate().getSchema() : null;

        // Проверки на наличие необходимых данных
        if (!StringUtils.hasText(sourceUrl)) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Не удалось найти URL в вашем запросе. Пожалуйста, укажите ссылку для создания сущности.", null);
        }
        if (entityName == null || !ontologyService.entityExists(entityName)) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Не удалось определить, какую именно сущность вы хотите создать по ссылке.", null);
        }

        try {
            String pageText = webPageContentExtractorService.extractTextFromUrl(sourceUrl);
            log.info("Текст с URL '{}' успешно извлечен.", sourceUrl);

            // Обрезка текста, если он слишком длинный для LLM
            if (pageText.length() > MAX_TEXT_LENGTH_FOR_LLM) {
                log.warn("Текст со страницы {} слишком длинный ({} символов), будет обрезан до {}", sourceUrl, pageText.length(), MAX_TEXT_LENGTH_FOR_LLM);
                pageText = pageText.substring(0, (int) MAX_TEXT_LENGTH_FOR_LLM);
            }

            // Формирование промпта для извлечения данных и отправка в LLM
            String dataExtractionPrompt = llmPromptService.buildDataExtractionPrompt(entityName, pageText);
            LLMResponseDto llmResponse = llmPromptService.sendPromptToLlm(dataExtractionPrompt, null, true, context.getChatMessage().getId());

            // Очистка и парсинг JSON-ответа от LLM
            String cleanedJson = llmPromptService.sanitizeLlmJsonResponse(llmResponse.getContent());
            Map<String, Object> extractedData = objectMapper.readValue(cleanedJson, new TypeReference<>() {});

            // Создание новой сущности в системе
            Map<String, Object> createdEntity = dynamicEntityQueryService.createEntity(entityName, extractedData);
            UUID newEntityId = UUID.fromString(String.valueOf(createdEntity.get("id")));
            log.info("Создана новая сущность '{}' с ID: {} на основе URL.", entityName, newEntityId);

            // Генерация FormView для созданной сущности
            JsonNode formViewSchema = dynamicViewGeneratorService.generateFormView(entityName, newEntityId);
            String successMessage = String.format("Отлично! %s '%s' создан(а) на основе данных со страницы. Открываю карточку.",
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

        } catch (IOException | IllegalArgumentException e) {
            log.error("Ошибка при обработке URL '{}': {}", sourceUrl, e.getMessage(), e);
            String userMessage = "Не удалось обработать данные по ссылке. " + e.getMessage();
            context.getChatMessage().setTemplate(null);
            return new ChatResponseDto(context.getSession().getId(), null, userMessage, null);
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при создании сущности '{}' из URL '{}': {}", entityName, sourceUrl, e.getMessage(), e);
            String errorMessage = "К сожалению, не удалось обработать ваш запрос. Произошла внутренняя ошибка.";
            context.getChatMessage().setTemplate(null);
            return new ChatResponseDto(context.getSession().getId(), null, errorMessage, null);
        }
    }
}