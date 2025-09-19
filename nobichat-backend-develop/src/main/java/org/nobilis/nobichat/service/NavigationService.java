package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.NavigationResponseDto;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.nobilis.nobichat.repository.UserChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NavigationService {

    private final UserChatSessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public NavigationResponseDto.NavigationInfoDto calculateNavigationInfo(UUID sessionId, ChatMessage activeMessage) {
        if (activeMessage == null || activeMessage.getTemplate() == null) {
            return NavigationResponseDto.NavigationInfoDto.builder()
                    .activePromptId(activeMessage != null ? activeMessage.getId() : null)
                    .canNavigateBack(false)
                    .canNavigateForward(false)
                    .build();
        }

        boolean canGoBack = chatMessageRepository.findPreviousUiMessage(sessionId, activeMessage.getCreationDate()).isPresent();
        boolean canGoForward = chatMessageRepository.findNextUiMessage(sessionId, activeMessage.getCreationDate()).isPresent();

        return NavigationResponseDto.NavigationInfoDto.builder()
                .activePromptId(activeMessage.getId())
                .canNavigateBack(canGoBack)
                .canNavigateForward(canGoForward)
                .build();
    }


    @Transactional
    public NavigationResponseDto navigate(UUID sessionId, String direction) {
        UserChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена."));

        ChatMessage currentMessage = session.getCurrentUiMessage();
        if (currentMessage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В сессии нет активного UI-состояния для навигации.");
        }

        Optional<ChatMessage> targetMessageOpt;
        if ("BACK".equalsIgnoreCase(direction)) {
            targetMessageOpt = chatMessageRepository.findPreviousUiMessage(sessionId, currentMessage.getCreationDate());
        } else if ("FORWARD".equalsIgnoreCase(direction)) {
            targetMessageOpt = chatMessageRepository.findNextUiMessage(sessionId, currentMessage.getCreationDate());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверное направление навигации: " + direction);
        }

        ChatMessage messageToDisplay = targetMessageOpt.orElse(currentMessage);

        if (targetMessageOpt.isPresent()) {
            session.setCurrentUiMessage(messageToDisplay);
            sessionRepository.save(session);
            log.info("Навигация в сессии {}: {} -> {}. Новый активный promptId: {}",
                    sessionId, direction, currentMessage.getId(), messageToDisplay.getId());
        } else {
            log.info("Навигация в сессии {}: {} -> достигнут край истории. Указатель остается на {}",
                    sessionId, direction, currentMessage.getId());
        }

        NavigationResponseDto.NavigationInfoDto navInfoDto = calculateNavigationInfo(sessionId, messageToDisplay);

        ChatResponseDto currentStateDto = new ChatResponseDto(
                sessionId,
                messageToDisplay.getTemplate().getSchema(),
                messageToDisplay.getAiResponseText(),
                navInfoDto
        );

        return new NavigationResponseDto(currentStateDto, navInfoDto);
    }

    @Transactional
    public NavigationResponseDto switchTo(UUID sessionId, UUID targetPromptId) {
        UserChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена."));

        ChatMessage targetMessage = chatMessageRepository.findById(targetPromptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Промпт с ID " + targetPromptId + " не найден."));

        if (!targetMessage.getSession().getId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Попытка переключиться на сообщение из другой сессии.");
        }

        if (targetMessage.getTemplate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нельзя переключиться на сообщение, у которого нет UI-схемы.");
        }

        session.setCurrentUiMessage(targetMessage);
        sessionRepository.save(session);
        log.info("Прямое переключение в сессии {}. Новый активный promptId: {}", sessionId, targetPromptId);

        NavigationResponseDto.NavigationInfoDto navInfoDto = calculateNavigationInfo(sessionId, targetMessage);

        ChatResponseDto currentStateDto = new ChatResponseDto(
                sessionId,
                targetMessage.getTemplate().getSchema(),
                targetMessage.getAiResponseText(),
                navInfoDto
        );

        return new NavigationResponseDto(currentStateDto, navInfoDto);
    }
}
