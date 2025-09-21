package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OntologyFieldRepository extends JpaRepository<OntologyFieldDefinition, UUID> {
}
