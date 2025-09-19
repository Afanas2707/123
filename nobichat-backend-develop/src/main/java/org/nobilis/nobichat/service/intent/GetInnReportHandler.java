package org.nobilis.nobichat.service.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.dto.kontur.KonturFocusCompanyDto;
import org.nobilis.nobichat.feign.KonturFocusFeignClient;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.repository.TemplateRepository;
import org.nobilis.nobichat.service.DynamicEntityQueryService;
import org.nobilis.nobichat.service.IntentHandler;
import org.nobilis.nobichat.service.LlmPromptService;
import org.nobilis.nobichat.service.OntologyService;
import org.nobilis.nobichat.service.WebPageContentExtractorService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Обработчик намерения "GET_INN_REPORT".
 * Получает краткий отчет по ИНН из Контур.Фокус и встраивает его в текущую UI-схему (карточку сущности).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetInnReportHandler implements IntentHandler {

    private static final Pattern INN_PATTERN = Pattern.compile("\\b(\\d{10}|\\d{12})\\b");

    private final KonturFocusFeignClient konturFocusFeignClient;
    private final WebPageContentExtractorService webPageContentExtractorService;
    private final LlmPromptService llmPromptService;
    private final DynamicEntityQueryService dynamicEntityQueryService;
    private final OntologyService ontologyService;
    private final ObjectMapper objectMapper;
    private final TemplateRepository templateRepository;


    @Override
    public String getIntentType() {
        return "GET_INN_REPORT";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatContext context) {
        JsonNode currentSchema = context.getCurrentUiMessage() != null && context.getCurrentUiMessage().getTemplate() != null
                ? context.getCurrentUiMessage().getTemplate().getSchema() : null;

        // Операция "Отчет по ИНН" требует открытой карточки сущности
        if (currentSchema == null) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Эта операция недоступна. Сначала откройте карточку сущности.", null);
        }

        JsonNode viewNode = currentSchema.path("view");

        // Проверка, что текущая схема является формой редактирования/просмотра
        String viewId = viewNode.path("id").asText("");
        if (!viewId.endsWith(".edit.form")) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Эта операция доступна только в режиме просмотра или редактирования карточки.", null);
        }

        String currentEntityName = viewNode.path("entity").asText(null);
        // sourceId может быть строкой, поэтому используем valueOf и then UUID.fromString
        UUID currentEntityId = null;
        if (viewNode.path("sourceId").isTextual()) {
            try {
                currentEntityId = UUID.fromString(viewNode.path("sourceId").asText());
            } catch (IllegalArgumentException e) {
                log.error("Invalid sourceId UUID in current UI schema: {}", viewNode.path("sourceId").asText(), e);
            }
        }


        if (currentEntityName == null || currentEntityId == null) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Не удалось определить текущую сущность из шаблона.", null);
        }

        // Пытаемся получить ИНН из текущей сущности
        Optional<Map<String, Object>> entityDataOpt = dynamicEntityQueryService.findEntityById(
                currentEntityName,
                currentEntityId,
                Collections.singletonList("inn") // Запрашиваем только поле ИНН
        );

        if (entityDataOpt.isEmpty()) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Не удалось загрузить данные для текущей сущности.", null);
        }

        Map<String, Object> entityData = entityDataOpt.get();
        String inn = entityData.get("inn") != null ? entityData.get("inn").toString().trim() : null;

        // Если ИНН не был найден в карточке, проверяем, не указал ли его пользователь явно в запросе
        if (inn == null || inn.isBlank()) {
            inn = intent.getInn(); // Используем ИНН, если LLM его выделила из запроса
        }

        // Финальная проверка наличия и корректности ИНН
        if (inn == null || inn.isBlank()) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "В карточке текущей сущности не заполнено поле ИНН или ИНН не указан в запросе.", null);
        }

        if (!INN_PATTERN.matcher(inn).matches()) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema,
                    String.format("Значение '%s' в поле ИНН не является корректным. ИНН должен состоять из 10 или 12 цифр.", inn), null
            );
        }

        // Если все проверки пройдены, переходим к получению отчета
        return processInnReportAndInject(context, inn, currentSchema);
    }

    private ChatResponseDto processInnReportAndInject(ChatContext context, String inn, JsonNode currentFormSchema) {
        try {
            log.info("Запрашиваем краткий отчет по ИНН: {}", inn);
            List<KonturFocusCompanyDto> briefReports = konturFocusFeignClient.briefReport(inn);

            if (briefReports == null || briefReports.isEmpty()) {
                return new ChatResponseDto(context.getSession().getId(), currentFormSchema, "Не удалось найти компанию по ИНН " + inn, null);
            }

            KonturFocusCompanyDto.BriefReportDto report = briefReports.get(0).getBriefReport();
            if (report == null || !StringUtils.hasText(report.getHref())) {
                return new ChatResponseDto(context.getSession().getId(), currentFormSchema, "Отчет Контур.Фокус не содержит ссылки на подробную информацию.", null);
            }

            String reportUrl = report.getHref();

            log.info("Скачиваем HTML-отчет по ссылке: {}", reportUrl);
            String htmlText = webPageContentExtractorService.extractTextFromUrl(reportUrl);

            final long MAX_TEXT_LENGTH_FOR_LLM = 30000;
            if (htmlText.length() > MAX_TEXT_LENGTH_FOR_LLM) {
                log.warn("Текст отчета слишком длинный, будет обрезан.");
                htmlText = htmlText.substring(0, (int) MAX_TEXT_LENGTH_FOR_LLM);
            }

            // Формирование промпта для суммаризации и отправка в LLM
            String summaryPrompt = llmPromptService.buildHtmlSummaryPrompt(htmlText);
            LLMResponseDto llmSummaryResponse = llmPromptService.sendPromptToLlm(summaryPrompt, null, true, context.getChatMessage().getId());

            String markdownContent = llmSummaryResponse.getContent();
            if (!StringUtils.hasText(markdownContent)) {
                return new ChatResponseDto(context.getSession().getId(), currentFormSchema, "Не удалось получить краткую сводку по компании.", null);
            }

            // Инъекция полученного содержимого в UI-схему
            JsonNode newSchema = injectContentIntoEmbeddedElements(currentFormSchema, markdownContent);
            log.info("В UI-схему успешно добавлен блок 'embeddedElements'.");

            // Сохранение обновленного шаблона и привязка его к сообщению чата
            Template newTemplate = Template.builder()
                    .schema(newSchema)
                    .mode(context.getChatMode())
                    .build();
            templateRepository.save(newTemplate);
            context.getChatMessage().setTemplate(newTemplate);

            String successMessage = "Отчет по ИНН " + inn + " успешно получен и добавлен в карточку.";
            return new ChatResponseDto(context.getSession().getId(), newSchema, successMessage, null);

        } catch (Exception e) {
            log.error("Ошибка при получении отчета по ИНН {}: {}", inn, e.getMessage(), e);
            String errorMessage = "Произошла ошибка при получении отчета по ИНН. Пожалуйста, попробуйте позже.";
            context.getChatMessage().setTemplate(null);
            return new ChatResponseDto(context.getSession().getId(), currentFormSchema, errorMessage, null);
        }
    }

    /**
     * Встраивает Markdown-контент в секцию `embeddedElements` UI-схемы.
     * @param originalSchema Исходная UI-схема.
     * @param markdownContent Markdown-контент для встраивания.
     * @return Модифицированная UI-схема.
     * @throws IllegalStateException если структура UI-схемы не соответствует ожиданиям.
     */
    private JsonNode injectContentIntoEmbeddedElements(JsonNode originalSchema, String markdownContent) {
        ObjectNode rootNode = (ObjectNode) originalSchema.deepCopy();

        JsonNode viewNodeJson = rootNode.get("view");

        if (viewNodeJson == null || !viewNodeJson.isObject()) {
            log.error("В UI-схеме отсутствует или поврежден узел 'view'. Невозможно вставить embeddedElements.");
            throw new IllegalStateException("В UI-схеме отсутствует или поврежден узел 'view'.");
        }
        ObjectNode viewNode = (ObjectNode) viewNodeJson;

        ArrayNode embeddedElements = objectMapper.createArrayNode();
        ObjectNode sourceElement = objectMapper.createObjectNode();
        sourceElement.put("source", markdownContent);
        embeddedElements.add(sourceElement);

        viewNode.set("embeddedElements", embeddedElements);

        return rootNode;
    }
}