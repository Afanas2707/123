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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Обработчик намерения "CREATE_ENTITY_FROM_DOCUMENT".
 * Создает сущность на основе данных, извлеченных из приложенного документа.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateEntityFromDocumentHandler implements IntentHandler {

    private final FileContentExtractorService fileContentExtractorService;
    private final LlmPromptService llmPromptService;
    private final ObjectMapper objectMapper;
    private final DynamicEntityQueryService dynamicEntityQueryService;
    private final OntologyService ontologyService;
    private final TemplateRepository templateRepository;
    private final DynamicViewGeneratorService dynamicViewGeneratorService;


    @Override
    public String getIntentType() {
        return "CREATE_ENTITY_FROM_DOCUMENT";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatContext context) {
        boolean hasAttachments = context.getAttachments() != null && !context.getAttachments().isEmpty();
        String entityName = intent.getEntity();
        JsonNode currentSchema = context.getCurrentUiMessage() != null && context.getCurrentUiMessage().getTemplate() != null
                ? context.getCurrentUiMessage().getTemplate().getSchema() : null;

        // Проверки на наличие необходимых данных
        if (!hasAttachments) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Для создания сущности из документа необходимо приложить один файл.", null);
        }
        if (context.getAttachments().size() > 1) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Для создания сущности можно приложить только один файл.", null);
        }
        if (entityName == null || !ontologyService.entityExists(entityName)) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Не удалось определить, какую именно сущность вы хотите создать из документа.", null);
        }

        MultipartFile document = context.getAttachments().get(0);

        try {
            String documentText = fileContentExtractorService.extractTextFromWord(document);
            log.info("Текст из файла '{}' успешно извлечен.", document.getOriginalFilename());

            // Формирование промпта для извлечения данных и отправка в LLM
            String dataExtractionPrompt = llmPromptService.buildDataExtractionPrompt(entityName, documentText);
            LLMResponseDto llmResponse = llmPromptService.sendPromptToLlm(dataExtractionPrompt, null, true, context.getChatMessage().getId());

            // Очистка и парсинг JSON-ответа от LLM
            String cleanedJson = llmPromptService.sanitizeLlmJsonResponse(llmResponse.getContent());
            Map<String, Object> extractedData = objectMapper.readValue(cleanedJson, new TypeReference<>() {});

            // Создание новой сущности в системе
            Map<String, Object> createdEntity = dynamicEntityQueryService.createEntity(entityName, extractedData);
            UUID newEntityId = UUID.fromString(String.valueOf(createdEntity.get("id")));
            log.info("Создана новая сущность '{}' с ID: {}", entityName, newEntityId);

            // Генерация FormView для созданной сущности
            JsonNode formViewSchema = dynamicViewGeneratorService.generateFormView(entityName, newEntityId);
            String successMessage = String.format("Отлично! %s '%s' создан(а) на основе документа. Открываю карточку.",
                    ontologyService.getEntityMetaData(entityName).getUserFriendlyName(),
                    createdEntity.getOrDefault("name", "Новая запись")
            );

            // Сохранение сгенерированного шаблона и привязка его к сообщению чата
            Template generatedTemplate = Template.builder()
                    .schema(formViewSchema)
                    .mode(context.getChatMode())
                    .build();
            templateRepository.save(generatedTemplate);
            log.info("Создан и сохранен Template (ID: {}) для карточки новой сущности.", generatedTemplate.getId());

            context.getChatMessage().setTemplate(generatedTemplate);

            return new ChatResponseDto(context.getSession().getId(), formViewSchema, successMessage, null);

        } catch (Exception e) {
            log.error("Произошла ошибка при создании сущности '{}' из документа: {}", entityName, e.getMessage());
            String errorMessage = "К сожалению, не удалось обработать ваш запрос. Пожалуйста, проверьте корректность файла и попробуйте еще раз.";

            // В случае ошибки, сбрасываем шаблон для текущего сообщения
            context.getChatMessage().setTemplate(null);

            return new ChatResponseDto(context.getSession().getId(), null, errorMessage, null);
        }
    }
}