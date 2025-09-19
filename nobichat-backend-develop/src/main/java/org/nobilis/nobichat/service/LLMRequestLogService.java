package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.model.LlmRequestLog;
import org.nobilis.nobichat.repository.LLMRequestLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LLMRequestLogService {

    private final LLMRequestLogRepository llmRequestLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(LlmRequestLog logEntry) {
        llmRequestLogRepository.save(logEntry);
    }
}