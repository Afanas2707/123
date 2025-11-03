package org.nobilis.nobichat.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.controller.ChatMessageController;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.exception.ChatFlowException;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.UserChatSessionRepository;
import org.nobilis.nobichat.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@RestControllerAdvice(assignableTypes = ChatMessageController.class)
@RequiredArgsConstructor
public class ChatControllerAdvice {

    private final ChatService chatService;
    private final UserChatSessionRepository sessionRepository;

    @ExceptionHandler(ChatFlowException.class)
    public ResponseEntity<ChatResponseDto> handleChatFlowException(ChatFlowException ex) {
        log.warn("Перехвачено управляемое исключение ChatFlowException: {}", ex.getMessage());

        UUID sessionId = (UUID) RequestContextHolder.currentRequestAttributes()
                .getAttribute("CURRENT_USER_CHAT_SESSION_ID", RequestAttributes.SCOPE_REQUEST);

        ChatResponseDto response = sessionRepository.findById(sessionId)
                    .map(chatService::rebuildLastState)
                    .orElseGet(ChatResponseDto::new);

        response.setSessionId(sessionId);
        response.setMessage(ex.getMessage());
        response.setErrors(ex.getErrors());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ChatResponseDto> handleAllChatExceptions(Exception ex) {
        String errorMessage = ex.getMessage();

        ChatResponseDto.ErrorDto error = ChatResponseDto.ErrorDto.builder().message(errorMessage).build();

        UUID sessionId = (UUID) RequestContextHolder.currentRequestAttributes()
                .getAttribute("CURRENT_USER_CHAT_SESSION_ID", RequestAttributes.SCOPE_REQUEST);

        UserChatSession session = sessionRepository.findById(sessionId).orElseThrow();
        ChatResponseDto response = chatService.rebuildLastState(session);
        response.setErrors(Collections.singletonList(error));
        response.setMessage("Произошла непредвиденная ошибка");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
