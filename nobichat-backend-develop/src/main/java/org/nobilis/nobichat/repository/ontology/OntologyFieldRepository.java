package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OntologyFieldRepository extends JpaRepository<OntologyField, UUID> {

    List<OntologyField> findByEntity_Name(String entityName);
}
