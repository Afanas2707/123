package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.Ontology;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OntologyStorageRepository extends JpaRepository<Ontology, UUID> {
    Optional<Ontology> findFirstByOrderByCreationDateDesc();
}
