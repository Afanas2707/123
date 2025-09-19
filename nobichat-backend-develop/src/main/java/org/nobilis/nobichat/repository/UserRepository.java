package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
}
