package org.nobilis.nobichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.ScenarioDraft;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ScenarioDraftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioConstructorService {

    private final ScenarioDraftRepository draftRepository;
    private final LlmPromptService llmPromptService;
    private final OntologyService ontologyService;
    private final ObjectMapper objectMapper;
    private final ScenarioFormatterService formatterService;
    private final ScenarioHelperService scenarioHelperService;
    private final ScenarioValidatorService validatorService;

    public ChatResponseDto processUserInput(ChatRequest request, UserChatSession session, ChatMessage chatMessage) {
        String userInput = request.getMessage();
        ScenarioDraft draft = draftRepository.findById(session.getActiveDraftId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Активный черновик сценария не найден."));

        try {
            String currentJson = objectMapper.writeValueAsString(draft.getDefinition());
            String ontologyContext = buildSimpleOntologyContext();
            String prompt = llmPromptService.buildScenarioEnrichmentPrompt(userInput, currentJson, ontologyContext);
            LLMResponseDto llmResponse = llmPromptService.sendPromptToLlm(prompt, null, false, chatMessage.getId());
            String updatedJson = llmPromptService.sanitizeLlmJsonResponse(llmResponse.getContent());

            ScenarioDefinition updatedDefinition = objectMapper.readValue(updatedJson, ScenarioDefinition.class);
            sanitizeDefinition(updatedDefinition);

            reindexSteps(updatedDefinition);
            autofillScenarioName(updatedDefinition);

            draft.setDefinition(updatedDefinition);
            if(StringUtils.hasText(updatedDefinition.getName())) {
                draft.setName(updatedDefinition.getName());
            }
            draftRepository.save(draft);

            validatorService.validateCorrectness(updatedDefinition);

            String finalMessage = determineNextQuestion(updatedDefinition);
            String markdownSummary = formatterService.formatScenarioAsMarkdown(updatedDefinition);
            return scenarioHelperService.createConstructorResponse(session, finalMessage, markdownSummary, updatedDefinition);

        } catch (JsonProcessingException e) {
            log.error("Критическая ошибка обработки JSON в конструкторе сценариев", e);
            throw new RuntimeException("Внутренняя ошибка сервера при обработке данных.", e);
        }
    }

    private void reindexSteps(ScenarioDefinition definition) {
        if (definition == null || CollectionUtils.isEmpty(definition.getSteps())) return;
        int index = 1;
        for (ScenarioDefinition.ScenarioStep step : definition.getSteps()) {
            step.setStepIndex(index++);
        }
    }

    private void autofillScenarioName(ScenarioDefinition definition) {
        if (!StringUtils.hasText(definition.getName()) &&
                !CollectionUtils.isEmpty(definition.getSteps()) &&
                StringUtils.hasText(definition.getSteps().get(0).getName()))
        {
            String defaultName = "Сценарий: " + definition.getSteps().get(0).getName();
            definition.setName(defaultName);
            log.info("Имя сценария не было задано. Установлено имя по умолчанию: '{}'", defaultName);
        }
    }

    private void sanitizeDefinition(ScenarioDefinition definition) {
        if (definition == null) return;
        if ("".equals(definition.getDescription())) {
            definition.setDescription(null);
        }
        if (definition.getSteps() != null) {
            for (ScenarioDefinition.ScenarioStep step : definition.getSteps()) {
                if ("".equals(step.getDescription())) {
                    step.setDescription(null);
                }
            }
        }
    }

    private String determineNextQuestion(ScenarioDefinition definition) {
        if (!StringUtils.hasText(definition.getName())) {
            return "Как назовем наш сценарий? Вы также можете дать ему краткое описание.";
        }
        if (!StringUtils.hasText(definition.getDescription())) {
            return String.format("Отличное название: '%s'! Теперь добавьте краткое описание.", definition.getName());
        }
        if (CollectionUtils.isEmpty(definition.getSteps())) {
            return "Описание принято. Давайте добавим первый шаг. Опишите его: название, описание, шаблон, сущность и поля.";
        }

        ScenarioDefinition.ScenarioStep lastStep = definition.getSteps().get(definition.getSteps().size() - 1);
        int stepIndex = lastStep.getStepIndex() != null ? lastStep.getStepIndex() : definition.getSteps().size();
        String stepIdentifier = StringUtils.hasText(lastStep.getName()) ? String.format("шага '%s'", lastStep.getName()) : String.format("шага №%d", stepIndex);

        if (!StringUtils.hasText(lastStep.getName())) return String.format("У шага №%d нет названия. Как его назовем?", stepIndex);
        if (!StringUtils.hasText(lastStep.getDescription())) return String.format("Для %s нужно добавить описание.", stepIdentifier);
        if (!StringUtils.hasText(lastStep.getTemplate())) return String.format("Для %s нужно выбрать шаблон (например, 'form' или 'list').", stepIdentifier);
        if (!StringUtils.hasText(lastStep.getEntity())) return String.format("Для %s нужно указать сущность (например, 'customer').", stepIdentifier);
        if (CollectionUtils.isEmpty(lastStep.getEntityFields())) return String.format("Сущность '%s' для %s выбрана. Какие поля нужно отобразить?", lastStep.getEntity(), stepIdentifier);

        return "Шаг полностью описан. Вы можете описать следующий шаг или сказать 'покажи', чтобы войти в режим просмотра.";
    }

    private String buildSimpleOntologyContext() {
        return ontologyService.getAllEntityMetaForPrompt().entrySet().stream()
                .map(entry -> {
                    String entityName = entry.getKey();
                    String fields = ontologyService.getFieldsForEntity(entityName).stream()
                            .map(f -> f.getName() + " (" + f.getUserFriendlyName() + ")")
                            .collect(Collectors.joining(", "));
                    return String.format("Сущность: %s. Поля: %s", entityName, fields);
                })
                .collect(Collectors.joining("\n"));
    }
}