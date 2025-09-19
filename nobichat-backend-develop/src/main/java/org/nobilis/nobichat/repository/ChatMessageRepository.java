package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.UserChatSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByCreationDateAsc(UUID sessionId);

    List<ChatMessage> findBySessionOrderByCreationDateDesc(UserChatSession session);

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.template.id = :newTemplateId WHERE cm.template.id IN :oldTemplateIds")
    Integer updateTemplateForMessages(@Param("newTemplateId") UUID newTemplateId, @Param("oldTemplateIds") List<UUID> oldTemplateIds);

    Optional<ChatMessage> findFirstBySessionIdAndTemplateIsNotNullOrderByCreationDateDesc(UUID sessionId);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session.id = :sessionId AND cm.template IS NOT NULL AND cm.creationDate < :currentDate ORDER BY cm.creationDate DESC")
    List<ChatMessage> findPreviousUiMessages(@Param("sessionId") UUID sessionId, @Param("currentDate") Instant currentDate, Pageable pageable);

    /**
     * Находит предыдущее сообщение с UI-шаблоном относительно указанного момента времени.
     */
    default Optional<ChatMessage> findPreviousUiMessage(UUID sessionId, Instant currentDate) {
        return findPreviousUiMessages(sessionId, currentDate, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session.id = :sessionId AND cm.template IS NOT NULL AND cm.creationDate > :currentDate ORDER BY cm.creationDate ASC")
    List<ChatMessage> findNextUiMessages(@Param("sessionId") UUID sessionId, @Param("currentDate") Instant currentDate, Pageable pageable);

    /**
     * Находит следующее сообщение с UI-шаблоном относительно указанного момента времени.
     */
    default Optional<ChatMessage> findNextUiMessage(UUID sessionId, Instant currentDate) {
        return findNextUiMessages(sessionId, currentDate, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("SELECT DISTINCT m FROM ChatMessage m LEFT JOIN FETCH m.attachments WHERE m IN :messages")
    List<ChatMessage> findWithAttachments(@Param("messages") List<ChatMessage> messages);

    List<ChatMessage> findBySessionAndCreationDateAfter(UserChatSession session, Instant creationDate);
}
