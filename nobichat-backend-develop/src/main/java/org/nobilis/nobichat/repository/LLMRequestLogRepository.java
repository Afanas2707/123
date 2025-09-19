package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.LlmRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LLMRequestLogRepository extends JpaRepository<LlmRequestLog, UUID> {
}
