package org.nobilis.nobichat.service;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiChatOptions defaultOptions;
    private final RateLimiterRegistry rateLimiterRegistry;

    public LLMResponseDto sendToSingleModel(String prompt, String modelName, Boolean useRateLimiter) {
        String targetModel;

        if (modelName == null || modelName.isBlank()) {
            targetModel = defaultOptions.getModel();
        } else {
            targetModel = modelName;
        }
        return callSingleModel(prompt, targetModel, useRateLimiter);
    }

    public LLMResponseDto callSingleModel(String prompt, String modelName, Boolean useRateLimiter) {
        if (useRateLimiter) {
            rateLimiterRegistry.rateLimiter("openRouterLimiter").acquirePermission();
        }

        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(modelName)
                    .temperature(0.1)
                    .build();

            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(options)
                    .build();

            long startTime = System.currentTimeMillis();
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            long endTime = System.currentTimeMillis();
            Duration responseDuration = Duration.ofMillis(endTime - startTime);

            Generation generation = response.getResult();
            Usage usage = response.getMetadata().getUsage();

            String content = (generation != null && generation.getOutput() != null)
                    ? generation.getOutput().getText() : "";

            return LLMResponseDto.builder()
                    .modelName(modelName)
                    .content(content)
                    .promptTokens(usage.getPromptTokens())
                    .completionTokens(usage.getCompletionTokens())
                    .totalTokens(usage.getTotalTokens())
                    .responseTime(responseDuration.toMillis() + "ms")
                    .build();
        } catch (Exception e) {
            log.error("Failed to get response from model '{}': {}", modelName, e.getMessage());
            return LLMResponseDto.builder()
                    .modelName(modelName)
                    .content("Error: " + e.getMessage())
                    .build();
        }
    }
}