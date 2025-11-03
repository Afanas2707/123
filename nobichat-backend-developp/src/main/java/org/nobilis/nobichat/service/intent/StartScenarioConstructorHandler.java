package org.nobilis.nobichat.service.intent;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.ScenarioDraft;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ScenarioDraftRepository;
import org.nobilis.nobichat.repository.UserChatSessionRepository;
import org.nobilis.nobichat.service.ScenarioConstructorService;
import org.nobilis.nobichat.service.ScenarioHelperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class StartScenarioConstructorHandler implements IntentHandler {

    private final ScenarioDraftRepository draftRepository;
    private final UserChatSessionRepository sessionRepository;
    private final ScenarioConstructorService constructorService;

    @Override
    public String getIntentType() {
        return "START_SCENARIO_CONSTRUCTOR";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        ScenarioDefinition initialDefinition = new ScenarioDefinition();
        ScenarioDefinition.ScenarioStep initialStep = new ScenarioDefinition.ScenarioStep();
        initialStep.setStepIndex(1);
        initialDefinition.setSteps(Collections.singletonList(initialStep));

        ScenarioDraft draft = new ScenarioDraft();
        draft.setDefinition(initialDefinition);
        draft = draftRepository.save(draft);

        session.setIsInConstructorMode(true);
        session.setActiveDraftId(draft.getId());

        sessionRepository.save(session);

        return constructorService.processUserInput(request, session, message);
    }
}