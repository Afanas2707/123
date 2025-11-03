package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByToken(String token);
    List<AuthToken> findAllByUserId(UUID userId);
}
