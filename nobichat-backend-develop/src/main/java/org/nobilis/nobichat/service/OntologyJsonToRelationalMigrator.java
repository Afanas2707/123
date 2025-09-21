package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.model.Ontology;
import org.nobilis.nobichat.repository.OntologyStorageRepository;
import org.nobilis.nobichat.repository.ontology.OntologyEntityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OntologyJsonToRelationalMigrator {

    private final OntologyStorageRepository ontologyStorageRepository;
    private final OntologySchemaPersistenceService schemaPersistenceService;
    private final OntologyEntityRepository ontologyEntityRepository;

    @Value("${ontology.migration.enabled:true}")
    private boolean migrationEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateOnStartup() {
        if (!migrationEnabled) {
            log.info("Ontology JSON migration is disabled by configuration.");
            return;
        }
        migrate();
    }

    @Transactional
    public void migrate() {
        if (ontologyEntityRepository.count() > 0) {
            log.debug("Ontology relational tables already contain data. Skipping migration from JSON.");
            return;
        }

        Optional<Ontology> legacyOntology = ontologyStorageRepository.findFirstByOrderByCreationDateDesc();
        if (legacyOntology.isEmpty() || legacyOntology.get().getSchema() == null) {
            log.info("No legacy ontology JSON found for migration.");
            return;
        }

        log.info("Migrating legacy ontology JSON payload into relational schema.");
        schemaPersistenceService.replaceSchema(legacyOntology.get().getSchema());
        log.info("Ontology migration completed successfully.");
    }
}
