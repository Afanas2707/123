package org.nobilis.nobichat.exception;

import lombok.Getter;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;

import java.util.Collections;
import java.util.List;

/**
 * Кастомное исключение для управляемых бизнес-ошибок в чате.
 * Несет в себе список ошибок, которые нужно показать пользователю.
 */
@Getter
public class ChatFlowException extends RuntimeException {

    private final List<ChatResponseDto.ErrorDto> errors;

    /**
     * Конструктор для одной ошибки.
     */
    public ChatFlowException(String message) {
        super(message);
        this.errors = Collections.singletonList(ChatResponseDto.ErrorDto.builder().message(message).build());
    }

    /**
     * Конструктор для нескольких ошибок.
     */
    public ChatFlowException(List<String> errorMessages) {
        super(String.join(", ", errorMessages));
        this.errors = errorMessages.stream()
                .map(msg -> ChatResponseDto.ErrorDto.builder().message(msg).build())
                .collect(java.util.stream.Collectors.toList());
    }
}
