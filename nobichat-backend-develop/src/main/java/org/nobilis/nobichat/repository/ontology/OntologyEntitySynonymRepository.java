package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyEntitySynonym;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OntologyEntitySynonymRepository extends JpaRepository<OntologyEntitySynonym, UUID> {
}
