package org.nobilis.nobichat.events.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.events.event.LlmCallEvent;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.LlmRequestLog;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.nobilis.nobichat.repository.LLMRequestLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRequestLogEventListener {

    private final LLMRequestLogRepository llmRequestLogRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Слушатель для событий, опубликованных ВНУТРИ транзакции (из ChatService).
     * Он ждет завершения этой транзакции, чтобы гарантировать видимость данных.
     * Сработает, только если у события есть chatMessageId.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, condition = "#event.chatMessageId != null")
    public void handleTransactionalLlmEventAfterCommit(LlmCallEvent event) {
        chatMessageRepository.findById(event.getChatMessageId()).ifPresentOrElse(
                chatMessage -> {
                    LlmRequestLog logEntry = createLogEntryFromEvent(event, chatMessage);
                    llmRequestLogRepository.save(logEntry);
                },
                () -> {
                    log.error("КРИТИЧЕСКАЯ ОШИБКА: Сообщение чата с ID {} не найдено после коммита транзакции! Лог будет сохранен без связи.", event.getChatMessageId());
                    LlmRequestLog logEntry = createLogEntryFromEvent(event, null);
                    llmRequestLogRepository.save(logEntry);
                }
        );
    }

    /**
     * Слушатель для событий, опубликованных ВНУТРИ транзакции(из ChatService), которая была отменена.
     * Сработает, только если у события есть chatMessageId.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK, condition = "#event.chatMessageId != null")
    public void handleTransactionalLlmEventAfterRollback(LlmCallEvent event) {
        LlmRequestLog logEntry = createLogEntryFromEvent(event, null);
        llmRequestLogRepository.save(logEntry);
    }

    /**
     * Слушатель для событий, опубликованных ВНЕ транзакции (из LLMController).
     */
    @Async
    @Transactional
    @EventListener(condition = "#event.chatMessageId == null")
    public void handleNonTransactionalLlmEvent(LlmCallEvent event) {
        LlmRequestLog logEntry = createLogEntryFromEvent(event, null);
        llmRequestLogRepository.save(logEntry);
    }

    private LlmRequestLog createLogEntryFromEvent(LlmCallEvent event, @Nullable ChatMessage chatMessage) {
        LLMResponseDto llmResponse = event.getLlmResponse();

        long responseTimeMs = -1L;
        if (llmResponse.getResponseTime() != null && llmResponse.getResponseTime().endsWith("ms")) {
            try {
                responseTimeMs = Long.parseLong(llmResponse.getResponseTime().replace("ms", ""));
            } catch (NumberFormatException e) {
                log.warn("Не удалось преобразовать время ответа из строки: '{}'", llmResponse.getResponseTime());
            }
        }

        return LlmRequestLog.builder()
                .timestamp(LocalDateTime.now())
                .prompt(event.getPrompt())
                .modelName(llmResponse.getModelName())
                .response(llmResponse.getContent())
                .promptTokens(llmResponse.getPromptTokens())
                .completionTokens(llmResponse.getCompletionTokens())
                .totalTokens(llmResponse.getTotalTokens())
                .responseTimeMs(responseTimeMs)
                .chatMessage(chatMessage)
                .build();
    }
}