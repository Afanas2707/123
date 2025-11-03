package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    @Query("SELECT a FROM Attachment a " +
            "JOIN a.chatMessage cm " +
            "JOIN cm.session s " +
            "WHERE a.id = :attachmentId AND cm.id = :promptId AND s.id = :sessionId")
    Optional<Attachment> findByIdAndChatMessageIdAndChatMessageSessionId(
            @Param("attachmentId") UUID attachmentId,
            @Param("promptId") UUID promptId,
            @Param("sessionId") UUID sessionId
    );
}
