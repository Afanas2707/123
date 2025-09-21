package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyFieldSynonym;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OntologyFieldSynonymRepository extends JpaRepository<OntologyFieldSynonym, UUID> {
}
