package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {
    Optional<Image> findByEntityIdAndEntityType(UUID entityId, EntityType entityType);

    Optional<Image> findByEntityType(EntityType entityType);
}
