package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OntologyRelationRepository extends JpaRepository<OntologyRelation, UUID> {

    List<OntologyRelation> findBySourceEntity_Name(String entityName);
}
