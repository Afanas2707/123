package org.nobilis.nobichat.service.intent;

import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;

public interface IntentHandler {

    /**
     * Возвращает тип намерения (intent), который обрабатывает данный хэндлер.
     * Соответствует полю 'intent' в IntentAndQueryResponse.
     * @return строковый идентификатор намерения
     */
    String getIntentType();

    /**
     * Обрабатывает запрос пользователя в соответствии с распознанным намерением.
     *
     * @param intentResponse Объект, содержащий распознанное намерение и связанные с ним данные (сущность, запрос, URL и т.д.).
     * @param context Объект, содержащий весь необходимый контекст для обработки запроса (сессия, сообщение, вложения и т.д.).
     * @return DTO ответа чата, содержащий UI-схему и текстовое сообщение.
     */
    ChatResponseDto handle(IntentAndQueryResponse intentResponse, ChatContext context);
}
