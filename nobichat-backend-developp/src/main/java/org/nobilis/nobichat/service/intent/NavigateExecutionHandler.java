package org.nobilis.nobichat.service.intent;

import lombok.RequiredArgsConstructor;
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

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NavigateExecutionHandler implements IntentHandler {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioHelperService scenarioHelperService;
    private final DynamicEntityQueryService dynamicEntityQueryService;

    @Override
    public String getIntentType() {
        return "NAVIGATE_EXECUTION";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        Scenario scenario = scenarioRepository.findById(session.getActiveScenarioId()).orElseThrow();
        ScenarioDefinition definition = scenario.getDefinition();

        ScenarioDefinition.ScenarioStep currentStep = definition.getSteps().stream()
                .filter(s -> s.getName().equals(session.getCurrentStepName()))
                .findFirst().orElseThrow();

        String direction = intent.getDirection();
        int targetIndex = "NEXT".equalsIgnoreCase(direction)
                ? currentStep.getStepIndex() + 1
                : currentStep.getStepIndex() - 1;

        Optional<ScenarioDefinition.ScenarioStep> targetStepOpt = definition.getSteps().stream()
                .filter(s -> s.getStepIndex() == targetIndex)
                .findFirst();

        if (targetStepOpt.isPresent()) {
            ScenarioDefinition.ScenarioStep targetStep = targetStepOpt.get();
            session.setCurrentStepName(targetStep.getName());

            UUID sourceId = null;
            if ("form".equalsIgnoreCase(targetStep.getTemplate())) {
                sourceId = dynamicEntityQueryService.findLastCreatedEntityId(targetStep.getEntity()).orElse(null);
            }

            return scenarioHelperService.createResponseForStep(session, definition, targetStep, sourceId, false);
        } else {
            if ("NEXT".equalsIgnoreCase(direction)) {
                return scenarioHelperService.createResponseForStep(session, definition, null, null, false);
            } else {
                String boundaryMessage = "Это первый шаг.";

                UUID sourceId = null;
                if ("form".equalsIgnoreCase(currentStep.getTemplate())) {
                    sourceId = dynamicEntityQueryService.findLastCreatedEntityId(currentStep.getEntity()).orElse(null);
                }

                ChatResponseDto response = scenarioHelperService.createResponseForStep(session, definition, currentStep, sourceId, false);
                response.setMessage(boundaryMessage);
                return response;
            }
        }
    }
}