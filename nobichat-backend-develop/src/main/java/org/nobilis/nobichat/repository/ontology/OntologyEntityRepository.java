package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OntologyEntityRepository extends JpaRepository<OntologyEntity, UUID> {

    @EntityGraph(attributePaths = {
            "fields",
            "fields.permission",
            "fields.synonyms",
            "relations",
            "relations.synonyms",
            "relations.targetEntity"
    })
    List<OntologyEntity> findAllWithDetails();

    @EntityGraph(attributePaths = {
            "fields",
            "fields.permission",
            "fields.synonyms",
            "relations",
            "relations.synonyms",
            "relations.targetEntity"
    })
    Optional<OntologyEntity> findByNameWithDetails(String name);
}
