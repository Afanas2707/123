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
import org.nobilis.nobichat.service.ScenarioValidatorService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class StartScenarioPreviewHandler implements IntentHandler {

    private final ScenarioDraftRepository draftRepository;
    private final ScenarioValidatorService validatorService;
    private final ScenarioHelperService scenarioHelperService;

    @Override
    public String getIntentType() {
        return "START_SCENARIO_PREVIEW";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        ScenarioDraft draft = draftRepository.findById(session.getActiveDraftId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Активный черновик не найден."));

        validatorService.validateCompleteness(draft.getDefinition());

        ScenarioDefinition.ScenarioStep firstStep = draft.getDefinition().getSteps().stream()
                .min(Comparator.comparingInt(ScenarioDefinition.ScenarioStep::getStepIndex))
                .orElseThrow(() -> new IllegalStateException("Сценарий не содержит шагов."));

        session.setIsInPreviewMode(true);
        session.setCurrentPreviewStepName(firstStep.getName());

        ChatResponseDto response = scenarioHelperService.createResponseForStep(session, draft.getDefinition(), firstStep, null, true);

        String augmentedMessage = response.getMessage() +
                "\n\nВы вошли в режим просмотра. Используйте 'дальше' и 'назад' для навигации или редактирования.";
        response.setMessage(augmentedMessage);

        return response;
    }
}