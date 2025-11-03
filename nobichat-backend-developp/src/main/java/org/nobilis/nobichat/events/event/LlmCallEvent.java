package org.nobilis.nobichat.events.event;

import lombok.Getter;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class LlmCallEvent extends ApplicationEvent {

    private final String prompt;
    private final LLMResponseDto llmResponse;
    private final UUID chatMessageId;

    public LlmCallEvent(Object source, String prompt, LLMResponseDto llmResponse, UUID chatMessageId) {
        super(source);
        this.prompt = prompt;
        this.llmResponse = llmResponse;
        this.chatMessageId = chatMessageId;
    }
}
