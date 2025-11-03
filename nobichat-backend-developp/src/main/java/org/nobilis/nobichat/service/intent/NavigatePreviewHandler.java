package org.nobilis.nobichat.service.intent;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.ScenarioDraft;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ScenarioDraftRepository;
import org.nobilis.nobichat.service.ScenarioHelperService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NavigatePreviewHandler implements IntentHandler {

    private final ScenarioDraftRepository draftRepository;
    private final ScenarioHelperService scenarioHelperService;

    @Override
    public String getIntentType() {
        return "NAVIGATE_PREVIEW";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        ScenarioDraft draft = draftRepository.findById(session.getActiveDraftId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Активный черновик не найден."));

        ScenarioDefinition definition = draft.getDefinition();

        ScenarioDefinition.ScenarioStep currentStep = definition.getSteps().stream()
                .filter(s -> s.getName().equals(session.getCurrentPreviewStepName()))
                .findFirst().orElseThrow();

        int targetIndex = "NEXT".equalsIgnoreCase(intent.getDirection())
                ? currentStep.getStepIndex() + 1
                : currentStep.getStepIndex() - 1;

        Optional<ScenarioDefinition.ScenarioStep> targetStepOpt = definition.getSteps().stream()
                .filter(s -> s.getStepIndex() == targetIndex)
                .findFirst();

        if (targetStepOpt.isPresent()) {
            session.setCurrentPreviewStepName(targetStepOpt.get().getName());
            return scenarioHelperService.createResponseForStep(session, definition, targetStepOpt.get(), null, true);
        } else {
            String boundaryMessage = "NEXT".equalsIgnoreCase(intent.getDirection())
                    ? "Это последний шаг."
                    : "Это первый шаг.";

            ChatResponseDto response = scenarioHelperService.createResponseForStep(session, definition, currentStep, null, true);

            response.setMessage(boundaryMessage);

            return response;
        }
    }
}