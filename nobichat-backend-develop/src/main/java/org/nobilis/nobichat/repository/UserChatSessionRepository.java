package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.model.UserChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserChatSessionRepository extends JpaRepository<UserChatSession, UUID> {
    List<UserChatSession> findByUserOrderByLastUpdateDateDesc(User user);

    @Query("SELECT s FROM UserChatSession s LEFT JOIN FETCH s.messages WHERE s.id = :sessionId")
    Optional<UserChatSession> findByIdWithMessages(@Param("sessionId") UUID sessionId);
}