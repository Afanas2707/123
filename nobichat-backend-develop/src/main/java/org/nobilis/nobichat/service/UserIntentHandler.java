package org.nobilis.nobichat.service;

import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserIntentHandler {

    /**
     * Обрабатывает запрос пользователя в соответствии со своей логикой.
     *
     * @param request        Оригинальный запрос от пользователя.
     * @param chatMessage    Сохраненная в БД сущность сообщения.
     * @param intentResponse Распознанное намерение и параметры от LLM.
     * @param attachments    Прикрепленные файлы (могут быть null).
     * @return DTO с ответом для отправки на UI.
     */
    ChatResponseDto handle(ChatRequest request,
                           ChatMessage chatMessage,
                           IntentAndQueryResponse intentResponse,
                           List<MultipartFile> attachments);

    /**
     * Возвращает строку с названием намерения, которое этот обработчик обслуживает.
     * Например, "GENERATE_LIST_VIEW".
     *
     * @return Имя намерения.
     */
    String getHandlingIntent();
}
