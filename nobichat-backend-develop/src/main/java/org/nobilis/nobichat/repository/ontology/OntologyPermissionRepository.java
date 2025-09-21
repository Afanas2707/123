package org.nobilis.nobichat.repository.ontology;

import org.nobilis.nobichat.model.ontology.OntologyPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OntologyPermissionRepository extends JpaRepository<OntologyPermission, UUID> {
}
