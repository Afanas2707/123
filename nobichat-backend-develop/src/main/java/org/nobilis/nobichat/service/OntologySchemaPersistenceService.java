package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.model.ontology.OntologyDbBinding;
import org.nobilis.nobichat.model.ontology.OntologyEntityDefinition;
import org.nobilis.nobichat.model.ontology.OntologyEntitySynonym;
import org.nobilis.nobichat.model.ontology.OntologyFieldDefinition;
import org.nobilis.nobichat.model.ontology.OntologyFieldSynonym;
import org.nobilis.nobichat.model.ontology.OntologyPermission;
import org.nobilis.nobichat.model.ontology.OntologyRelationDefinition;
import org.nobilis.nobichat.model.ontology.OntologyRelationSynonym;
import org.nobilis.nobichat.model.ui.UiComponent;
import org.nobilis.nobichat.repository.ontology.OntologyDbBindingRepository;
import org.nobilis.nobichat.repository.ontology.OntologyEntityRepository;
import org.nobilis.nobichat.repository.ontology.OntologyEntitySynonymRepository;
import org.nobilis.nobichat.repository.ontology.OntologyFieldRepository;
import org.nobilis.nobichat.repository.ontology.OntologyFieldSynonymRepository;
import org.nobilis.nobichat.repository.ontology.OntologyPermissionRepository;
import org.nobilis.nobichat.repository.ontology.OntologyRelationRepository;
import org.nobilis.nobichat.repository.ontology.OntologyRelationSynonymRepository;
import org.nobilis.nobichat.repository.ui.UiComponentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologySchemaPersistenceService {

    private final OntologyEntityRepository ontologyEntityRepository;
    private final OntologyFieldRepository ontologyFieldRepository;
    private final OntologyRelationRepository ontologyRelationRepository;
    private final OntologyEntitySynonymRepository ontologyEntitySynonymRepository;
    private final OntologyFieldSynonymRepository ontologyFieldSynonymRepository;
    private final OntologyRelationSynonymRepository ontologyRelationSynonymRepository;
    private final OntologyPermissionRepository ontologyPermissionRepository;
    private final OntologyDbBindingRepository ontologyDbBindingRepository;
    private final UiComponentRepository uiComponentRepository;

    @Transactional
    public void replaceSchema(OntologyDto ontologyDto) {
        clearExistingSchema();

        if (ontologyDto == null || ontologyDto.getEntities() == null || ontologyDto.getEntities().isEmpty()) {
            log.info("Incoming ontology DTO is empty. No relational data will be persisted.");
            return;
        }

        List<OntologyEntityDefinition> entitiesToPersist = new ArrayList<>();
        for (Map.Entry<String, OntologyDto.EntitySchema> entry : ontologyDto.getEntities().entrySet()) {
            OntologyEntityDefinition entity = mapEntity(entry.getKey(), entry.getValue());
            entitiesToPersist.add(entity);
        }

        ontologyEntityRepository.saveAll(entitiesToPersist);
        log.info("Persisted {} ontology entities to relational storage.", entitiesToPersist.size());
    }

    private void clearExistingSchema() {
        ontologyRelationSynonymRepository.deleteAll();
        ontologyFieldSynonymRepository.deleteAll();
        ontologyEntitySynonymRepository.deleteAll();
        ontologyRelationRepository.deleteAll();
        ontologyFieldRepository.deleteAll();
        ontologyEntityRepository.deleteAll();
        ontologyPermissionRepository.deleteAll();
        ontologyDbBindingRepository.deleteAll();
    }

    private OntologyEntityDefinition mapEntity(String entityName, OntologyDto.EntitySchema schema) {
        OntologyEntityDefinition entityDefinition = new OntologyEntityDefinition();
        entityDefinition.setName(entityName);

        if (schema != null && schema.getMeta() != null) {
            OntologyDto.Meta meta = schema.getMeta();
            entityDefinition.setUserFriendlyName(meta.getUserFriendlyName());
            entityDefinition.setUserFriendlyNameAccusative(meta.getUserFriendlyNameAccusative());
            entityDefinition.setUserFriendlyNamePlural(meta.getUserFriendlyNamePlural());
            entityDefinition.setUserFriendlyNamePluralGenitive(meta.getUserFriendlyNamePluralGenitive());
            entityDefinition.setEntityNamePlural(meta.getEntityNamePlural());
            entityDefinition.setDescription(meta.getDescription());
            entityDefinition.setPrimaryTable(meta.getPrimaryTable());
            entityDefinition.setDefaultSearchField(meta.getDefaultSearchField());
            entityDefinition.setPermissions(createPermissions(meta.getPermissions()));

            List<String> synonyms = meta.getSynonyms();
            if (synonyms != null) {
                synonyms.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .forEach(value -> {
                            OntologyEntitySynonym synonym = new OntologyEntitySynonym();
                            synonym.setEntity(entityDefinition);
                            synonym.setValue(value);
                            entityDefinition.getSynonyms().add(synonym);
                        });
            }
        }

        List<OntologyDto.EntitySchema.FieldSchema> fieldSchemas =
                schema != null && schema.getFields() != null ? schema.getFields() : Collections.emptyList();
        for (OntologyDto.EntitySchema.FieldSchema fieldSchema : fieldSchemas) {
            OntologyFieldDefinition fieldDefinition = mapField(entityDefinition, fieldSchema);
            entityDefinition.getFields().add(fieldDefinition);
        }

        Map<String, OntologyDto.EntitySchema.RelationSchema> relationSchemas =
                schema != null && schema.getRelations() != null ? schema.getRelations() : Collections.emptyMap();
        for (Map.Entry<String, OntologyDto.EntitySchema.RelationSchema> relationEntry : relationSchemas.entrySet()) {
            OntologyRelationDefinition relationDefinition =
                    mapRelation(entityDefinition, relationEntry.getKey(), relationEntry.getValue());
            entityDefinition.getRelations().add(relationDefinition);
        }

        return entityDefinition;
    }

    private OntologyFieldDefinition mapField(OntologyEntityDefinition entity, OntologyDto.EntitySchema.FieldSchema fieldSchema) {
        OntologyFieldDefinition fieldDefinition = new OntologyFieldDefinition();
        fieldDefinition.setEntity(entity);
        fieldDefinition.setName(fieldSchema.getName());
        fieldDefinition.setType(fieldSchema.getType());
        fieldDefinition.setDescription(fieldSchema.getDescription());
        fieldDefinition.setUserFriendlyName(fieldSchema.getUserFriendlyName());
        fieldDefinition.setPermissions(createPermissions(fieldSchema.getPermissions()));
        fieldDefinition.setDbBinding(createDbBinding(fieldSchema.getDb()));
        fieldDefinition.setQueryable(fieldSchema.isQueryable());
        fieldDefinition.setDefaultInList(fieldSchema.isDefaultInList());
        fieldDefinition.setMandatoryInList(fieldSchema.isMandatoryInList());
        fieldDefinition.setDefaultInCard(fieldSchema.isDefaultInCard());
        fieldDefinition.setListComponent(resolveComponent(fieldSchema.getListComponentId()));
        fieldDefinition.setFormComponent(resolveComponent(fieldSchema.getFormComponentId()));

        if (fieldSchema.getSynonyms() != null) {
            fieldSchema.getSynonyms().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> {
                        OntologyFieldSynonym synonym = new OntologyFieldSynonym();
                        synonym.setField(fieldDefinition);
                        synonym.setValue(value);
                        fieldDefinition.getSynonyms().add(synonym);
                    });
        }

        return fieldDefinition;
    }

    private OntologyRelationDefinition mapRelation(OntologyEntityDefinition entity,
                                                   String relationName,
                                                   OntologyDto.EntitySchema.RelationSchema relationSchema) {
        OntologyRelationDefinition relationDefinition = new OntologyRelationDefinition();
        relationDefinition.setEntity(entity);
        relationDefinition.setName(relationName);

        if (relationSchema != null) {
            relationDefinition.setType(relationSchema.getType());
            relationDefinition.setTargetEntityName(relationSchema.getTargetEntity());
            relationDefinition.setSourceTable(relationSchema.getSourceTable());
            relationDefinition.setSourceColumn(relationSchema.getSourceColumn());
            relationDefinition.setTargetTable(relationSchema.getTargetTable());
            relationDefinition.setTargetColumn(relationSchema.getTargetColumn());
            relationDefinition.setJoinCondition(relationSchema.getJoinCondition());
            relationDefinition.setFetchStrategy(relationSchema.getFetchStrategy());
            relationDefinition.setLabel(relationSchema.getLabel());
            relationDefinition.setTabId(relationSchema.getTabId());
            relationDefinition.setDefaultInCard(relationSchema.isDefaultInCard());
            relationDefinition.setDisplaySequence(relationSchema.getDisplaySequence());
            relationDefinition.setComponent(resolveComponent(relationSchema.getComponentId()));

            if (relationSchema.getSynonyms() != null) {
                relationSchema.getSynonyms().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .forEach(value -> {
                            OntologyRelationSynonym synonym = new OntologyRelationSynonym();
                            synonym.setRelation(relationDefinition);
                            synonym.setValue(value);
                            relationDefinition.getSynonyms().add(synonym);
                        });
            }
        }

        return relationDefinition;
    }

    private OntologyPermission createPermissions(OntologyDto.Permissions permissionsDto) {
        if (permissionsDto == null) {
            return null;
        }
        OntologyPermission permission = new OntologyPermission();
        permission.setCanRead(permissionsDto.isRead());
        permission.setCanWrite(permissionsDto.isWrite());
        return permission;
    }

    private OntologyDbBinding createDbBinding(OntologyDto.EntitySchema.FieldSchema.DbInfo dbInfo) {
        if (dbInfo == null) {
            return null;
        }
        OntologyDbBinding dbBinding = new OntologyDbBinding();
        dbBinding.setTableName(dbInfo.getTable());
        dbBinding.setColumnName(dbInfo.getColumn());
        dbBinding.setPrimaryKey(dbInfo.getIsPrimaryKey());
        dbBinding.setRelationName(dbInfo.getRelationName());
        return dbBinding;
    }

    private UiComponent resolveComponent(UUID componentId) {
        if (componentId == null) {
            return null;
        }
        return uiComponentRepository.findById(componentId).orElse(null);
    }
}
