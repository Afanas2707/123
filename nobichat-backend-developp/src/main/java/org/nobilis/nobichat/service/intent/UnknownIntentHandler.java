package org.nobilis.nobichat.service.intent;


import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.UserChatSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class UnknownIntentHandler implements IntentHandler {

    private final Random random = new Random();

    public UnknownIntentHandler() {}

    @Override
    public String getIntentType() {
        return "UNKNOWN";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        String notFoundMessage = generateNotFoundResponseMessage();

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .message(notFoundMessage)
                .build();
    }

    private String generateNotFoundResponseMessage() {
        List<String> messages = List.of(
                "Извините, я не совсем понял, что вы хотите. Попробуйте переформулировать запрос.",
                "Хм, я не смог распознать вашу команду. Можете уточнить?",
                "К сожалению, я не могу обработать этот запрос. Пожалуйста, попробуйте другую команду."
        );
        return messages.get(random.nextInt(messages.size()));
    }
}