package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyEntityDefinition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OntologyEntityRepository extends JpaRepository<OntologyEntityDefinition, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "synonyms",
            "fields",
            "fields.synonyms",
            "fields.dbBinding",
            "fields.permissions",
            "relations",
            "relations.synonyms",
            "permissions"
    })
    List<OntologyEntityDefinition> findAll();

    @EntityGraph(attributePaths = {
            "synonyms",
            "fields",
            "fields.synonyms",
            "fields.dbBinding",
            "fields.permissions",
            "relations",
            "relations.synonyms",
            "permissions"
    })
    Optional<OntologyEntityDefinition> findByName(String name);
}
