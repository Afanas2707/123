package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyRelationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OntologyRelationRepository extends JpaRepository<OntologyRelationDefinition, UUID> {
}
