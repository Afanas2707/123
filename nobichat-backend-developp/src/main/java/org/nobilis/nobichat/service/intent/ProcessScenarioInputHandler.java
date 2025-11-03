package org.nobilis.nobichat.service.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Scenario;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ScenarioRepository;
import org.nobilis.nobichat.service.DynamicEntityQueryService;
import org.nobilis.nobichat.service.ScenarioHelperService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessScenarioInputHandler implements IntentHandler {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioHelperService scenarioHelperService;
    private final ObjectMapper objectMapper;
    private final DynamicEntityQueryService dynamicEntityQueryService;

    @Override
    public String getIntentType() {
        return "PROCESS_SCENARIO_INPUT";
    }

    @SneakyThrows
    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        Scenario scenario = scenarioRepository.findById(session.getActiveScenarioId())
                .orElseThrow(() -> new IllegalStateException("Активный сценарий не найден, хотя сессия находится в режиме исполнения."));

        ScenarioDefinition definition = scenario.getDefinition();

        ScenarioDefinition.ScenarioStep currentStep = definition.getSteps().stream()
                .filter(s -> s.getName().equals(session.getCurrentStepName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Текущий шаг сценария не найден в определении."));

        Map<String, Object> extractedData = intent.getScenarioInputData();

        if (extractedData != null && !extractedData.isEmpty()) {

            String currentContextJson = session.getScenarioContextJson();
            Map<String, Object> contextMap = objectMapper.readValue(currentContextJson, new TypeReference<>() {
            });

            contextMap.putAll(extractedData);

            session.setScenarioContextJson(objectMapper.writeValueAsString(contextMap));
            log.info("Обновлен контекст сценария для сессии {}: {}", session.getId(), session.getScenarioContextJson());

        }

        int nextStepIndex = currentStep.getStepIndex() + 1;
        Optional<ScenarioDefinition.ScenarioStep> nextStepOpt = definition.getSteps().stream()
                .filter(s -> s.getStepIndex() == nextStepIndex)
                .findFirst();

        if (nextStepOpt.isPresent()) {
            ScenarioDefinition.ScenarioStep nextStep = nextStepOpt.get();
            session.setCurrentStepName(nextStep.getName());

            UUID sourceId = null;
            if ("form".equalsIgnoreCase(nextStep.getTemplate())) {
                sourceId = dynamicEntityQueryService.findLastCreatedEntityId(nextStep.getEntity()).orElse(null);
            }
            return scenarioHelperService.createResponseForStep(session, definition, nextStep, sourceId, false);
        } else {
            return scenarioHelperService.createResponseForStep(session, definition, null, null, false);
        }
    }
}