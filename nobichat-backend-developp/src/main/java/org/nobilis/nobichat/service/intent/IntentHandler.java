package org.nobilis.nobichat.service.intent;

import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.UserChatSession;

public interface IntentHandler {

    /**
     * Возвращает тип намерения (intent), который обрабатывает данный хэндлер.
     * @return строковый идентификатор намерения.
     */
    String getIntentType();

    /**
     * Обрабатывает запрос пользователя в соответствии с распознанным намерением.
     * @param intent Распознанное намерение и его параметры.
     * @param request Оригинальный запрос пользователя.
     * @param session Текущая сессия чата.
     * @param message Текущее обрабатываемое сообщение.
     * @return DTO ответа чата.
     */
    ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message);
}