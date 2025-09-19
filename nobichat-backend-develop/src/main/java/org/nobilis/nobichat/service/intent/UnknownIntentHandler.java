package org.nobilis.nobichat.service.intent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.service.IntentHandler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service // Аннотация Service, чтобы Spring мог найти и инжектировать этот класс, если он будет нужен где-то еще.
@Slf4j
public class UnknownIntentHandler implements IntentHandler {

    private final Random random = new Random();

    // Поля для случаев, когда UnknownIntentHandler создается напрямую в ChatService
    // (например, при неизвестном интенте и отсутствии активного UI-сообщения в контексте).
    // Они будут null, если класс инжектирован Spring'ом.
    private final UUID sessionId;
    private final JsonNode fallbackSchema;

    /**
     * Конструктор по умолчанию для инжекции Spring'ом.
     * Поля sessionId и fallbackSchema будут null.
     */
    public UnknownIntentHandler() {
        this.sessionId = null;
        this.fallbackSchema = null;
    }

    /**
     * Специальный конструктор для использования в ChatService.processRequestInternal
     * когда handlerRegistry.getHandler() не находит подходящего обработчика.
     * Это позволяет вернуть дефолтный ответ, даже если `ChatContext` не полностью
     * сформирован (например, `currentUiMessage` отсутствует или без шаблона).
     *
     * @param sessionId ID сессии чата.
     * @param fallbackSchema Схема UI для использования в качестве запасной (например, текущая активная схема).
     */
    public UnknownIntentHandler(UUID sessionId, JsonNode fallbackSchema) {
        this.sessionId = sessionId;
        this.fallbackSchema = fallbackSchema;
    }


    @Override
    public String getIntentType() {
        return "UNKNOWN";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intentResponse, ChatContext context) {
        // Определяем ID сессии для ответа. Приоритет отдается context, если он есть.
        UUID actualSessionId = (context != null && context.getSession() != null) ? context.getSession().getId() : this.sessionId;

        // Определяем схему UI для ответа.
        // 1. Пытаемся взять из currentUiMessage в контексте.
        // 2. Если нет, используем fallbackSchema, переданную в конструкторе (если этот экземпляр был создан ChatService).
        // 3. Если и этого нет, то схема будет null.
        JsonNode schemaToUse = null;
        if (context != null && context.getCurrentUiMessage() != null && context.getCurrentUiMessage().getTemplate() != null) {
            schemaToUse = context.getCurrentUiMessage().getTemplate().getSchema();
        } else if (this.fallbackSchema != null) {
            schemaToUse = this.fallbackSchema;
        }

        // Генерируем случайное сообщение о непонимании
        String notFoundMessage = generateNotFoundResponseMessage();

        // Возвращаем ChatResponseDto с сообщением и (опционально) текущей UI-схемой
        return new ChatResponseDto(actualSessionId, schemaToUse, notFoundMessage, null);
    }

    /**
     * Генерирует случайное сообщение, указывающее на то, что запрос не был понят.
     * @return Случайное сообщение.
     */
    private String generateNotFoundResponseMessage() {
        List<String> messages = List.of(
                "Извините, я не совсем понял, что вы хотите открыть. Попробуйте переформулировать запрос.",
                "Хм, я не смог найти подходящий интерфейс для вашего запроса. Можете уточнить?",
                "К сожалению, я не могу обработать этот запрос. Пожалуйста, попробуйте другую команду."
        );
        return messages.get(random.nextInt(messages.size()));
    }
}