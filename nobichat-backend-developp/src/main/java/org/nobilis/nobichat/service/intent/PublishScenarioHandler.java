package org.nobilis.nobichat.service.intent;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.exception.ChatFlowException;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Scenario;
import org.nobilis.nobichat.model.ScenarioDraft;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ScenarioDraftRepository;
import org.nobilis.nobichat.repository.ScenarioRepository;
import org.nobilis.nobichat.service.ScenarioValidatorService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PublishScenarioHandler implements IntentHandler {

    private final ScenarioDraftRepository draftRepository;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioValidatorService validatorService;

    @Override
    public String getIntentType() {
        return "PUBLISH_SCENARIO";
    }

    @Override
    @Transactional
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        if (!Boolean.TRUE.equals(session.getIsInConstructorMode()) || session.getActiveDraftId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сейчас нет активного сценария для публикации.");
        }

        ScenarioDraft draft = draftRepository.findById(session.getActiveDraftId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Активный черновик не найден."));

        validatorService.validateCompleteness(draft.getDefinition());

        String scenarioName = draft.getDefinition().getName();
        if (scenarioRepository.existsByNameInDefinition(scenarioName)) {
            throw new ChatFlowException(String.format("Сценарий с названием '%s' уже существует.", scenarioName));
        }

        Scenario scenario = new Scenario();
        scenario.setDefinition(draft.getDefinition());
        scenarioRepository.save(scenario);

        session.setIsInConstructorMode(false);
        session.setIsInPreviewMode(false);
        session.setActiveDraftId(null);
        session.setCurrentPreviewStepName(null);

        String successMessage = String.format("Сценарий '%s' успешно опубликован! Вы вернулись на главный экран.", scenarioName);

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .message(successMessage)
                .build();
    }
}