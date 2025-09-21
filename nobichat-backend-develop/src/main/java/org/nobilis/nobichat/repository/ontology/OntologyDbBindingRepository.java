package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyDbBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OntologyDbBindingRepository extends JpaRepository<OntologyDbBinding, UUID> {
}
