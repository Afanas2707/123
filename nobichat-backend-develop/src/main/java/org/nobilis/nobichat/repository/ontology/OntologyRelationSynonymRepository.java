package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyRelationSynonym;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OntologyRelationSynonymRepository extends JpaRepository<OntologyRelationSynonym, UUID> {
}
