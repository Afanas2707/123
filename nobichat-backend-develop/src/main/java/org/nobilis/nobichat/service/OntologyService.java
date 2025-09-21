package org.nobilis.nobichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.nobilis.nobichat.constants.OntologyVersion;
import org.nobilis.nobichat.dto.ConfluencePageResponse;
import org.nobilis.nobichat.dto.ontology.EntityMetaData;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.feign.ConfluenceClient;
import org.nobilis.nobichat.model.Ontology;
import org.nobilis.nobichat.model.ontology.FieldDbMetadata;
import org.nobilis.nobichat.model.ontology.OntologyEntity;
import org.nobilis.nobichat.model.ontology.OntologyField;
import org.nobilis.nobichat.model.ontology.OntologyFieldPermission;
import org.nobilis.nobichat.model.ontology.OntologyFieldSynonym;
import org.nobilis.nobichat.model.ontology.OntologyRelation;
import org.nobilis.nobichat.model.ontology.OntologyRelationSynonym;
import org.nobilis.nobichat.repository.OntologyStorageRepository;
import org.nobilis.nobichat.repository.ontology.OntologyEntityRepository;
import org.nobilis.nobichat.repository.ontology.OntologyFieldRepository;
import org.nobilis.nobichat.repository.ontology.OntologyRelationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyService {

    private final OntologyStorageRepository ontologyRepository;
    private final OntologyEntityRepository ontologyEntityRepository;
    private final OntologyFieldRepository ontologyFieldRepository;
    private final OntologyRelationRepository ontologyRelationRepository;
    private final ObjectMapper objectMapper;
    private final ConfluenceClient confluenceClient;

    @Value("${confluence.api.ontology-page-id}")
    private String ontologyPageId;

    @Transactional
    public OntologyDto updateOntologyFromFile(OntologyVersion version) {
        String fileName = "ontology-" + version.name().toLowerCase() + ".json";
        log.info("Запуск обновления онтологии из файла ресурсов: {}", fileName);

        String ontologyJsonString = loadResourceFileAsString(fileName);

        OntologyDto newOntologyDto;
        try {
            newOntologyDto = objectMapper.readValue(ontologyJsonString, OntologyDto.class);
            log.info("JSON из файла {} успешно десериализован в OntologyDto.", fileName);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при десериализации JSON онтологии из файла {}: {}", fileName, e.getMessage(), e);
            throw new IllegalArgumentException("Некорректный JSON формат онтологии в файле: " + e.getMessage(), e);
        }

        return updateOntology(newOntologyDto);
    }

    private String loadResourceFileAsString(String path) {
        try (var reader = new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            log.error("Критическая ошибка при загрузке ресурса: {}", path, e);
            throw new RuntimeException("Ошибка при загрузке ресурса: " + path, e);
        }
    }

    private OntologyDto getOntologyFromDb() {
        List<OntologyEntity> normalizedEntities = ontologyEntityRepository.findAllWithDetails();
        if (!normalizedEntities.isEmpty()) {
            return assembleOntologyDto(normalizedEntities);
        }
        return ontologyRepository.findFirstByOrderByCreationDateDesc()
                .map(Ontology::getSchema)
                .orElseThrow(() -> new ResourceNotFoundException("Отсутствует онтология в БД."));
    }

    private OntologyDto assembleOntologyDto(List<OntologyEntity> entities) {
        OntologyDto ontologyDto = new OntologyDto();
        for (OntologyEntity entity : entities) {
            OntologyDto.EntitySchema entitySchema = new OntologyDto.EntitySchema();
            entitySchema.setMeta(mapMeta(entity));
            entitySchema.setFields(mapFields(entity));
            entitySchema.setRelations(mapRelations(entity));
            ontologyDto.getEntities().put(entity.getName(), entitySchema);
        }

        return ontologyDto;
    }

    private OntologyDto.Meta mapMeta(OntologyEntity entity) {
        OntologyDto.Meta meta = new OntologyDto.Meta();
        meta.setUserFriendlyName(entity.getUserFriendlyName());
        meta.setUserFriendlyNameAccusative(entity.getUserFriendlyNameAccusative());
        meta.setUserFriendlyNamePlural(entity.getUserFriendlyNamePlural());
        meta.setUserFriendlyNamePluralGenitive(entity.getUserFriendlyNamePluralGenitive());
        meta.setEntityNamePlural(entity.getEntityNamePlural());
        meta.setDescription(entity.getDescription());
        meta.setPrimaryTable(entity.getPrimaryTable());
        meta.setDefaultSearchField(entity.getDefaultSearchField());
        meta.setSynonyms(Optional.ofNullable(entity.getSynonyms()).orElseGet(ArrayList::new));
        meta.setPermissions(new OntologyDto.Permissions(entity.isPermissionRead(), entity.isPermissionWrite()));
        return meta;
    }

    private List<OntologyDto.EntitySchema.FieldSchema> mapFields(OntologyEntity entity) {
        if (entity.getFields() == null) {
            return new ArrayList<>();
        }
        return entity.getFields().stream()
                .map(this::mapField)
                .collect(Collectors.toList());
    }

    private OntologyDto.EntitySchema.FieldSchema mapField(OntologyField field) {
        OntologyDto.EntitySchema.FieldSchema schema = new OntologyDto.EntitySchema.FieldSchema();
        schema.setName(field.getName());
        schema.setType(field.getType());
        schema.setDescription(field.getDescription());
        schema.setUserFriendlyName(field.getUserFriendlyName());
        schema.setUi(field.getUi());

        FieldDbMetadata metadata = field.getDbMetadata();
        if (metadata != null) {
            OntologyDto.EntitySchema.FieldSchema.DbInfo dbInfo = new OntologyDto.EntitySchema.FieldSchema.DbInfo();
            dbInfo.setTable(metadata.getTableName());
            dbInfo.setColumn(metadata.getColumnName());
            dbInfo.setIsPrimaryKey(metadata.isPrimaryKey());
            dbInfo.setRelationName(metadata.getRelationName());
            schema.setDb(dbInfo);
        }

        List<String> synonyms = field.getSynonyms() == null ? Collections.emptyList() : field.getSynonyms().stream()
                .map(OntologyFieldSynonym::getValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        schema.setSynonyms(new ArrayList<>(synonyms));

        OntologyFieldPermission permission = field.getPermission();
        if (permission != null) {
            schema.setPermissions(new OntologyDto.Permissions(permission.isCanRead(), permission.isCanWrite()));
        }

        return schema;
    }

    private Map<String, OntologyDto.EntitySchema.RelationSchema> mapRelations(OntologyEntity entity) {
        Map<String, OntologyDto.EntitySchema.RelationSchema> relations = new LinkedHashMap<>();
        if (entity.getRelations() == null) {
            return relations;
        }
        for (OntologyRelation relation : entity.getRelations()) {
            OntologyDto.EntitySchema.RelationSchema schema = new OntologyDto.EntitySchema.RelationSchema();
            schema.setType(relation.getType());
            String targetEntityName = Optional.ofNullable(relation.getTargetEntity())
                    .map(OntologyEntity::getName)
                    .orElse(relation.getTargetEntityName());
            schema.setTargetEntity(targetEntityName);
            schema.setSourceTable(relation.getSourceTable());
            schema.setSourceColumn(relation.getSourceColumn());
            schema.setTargetTable(relation.getTargetTable());
            schema.setTargetColumn(relation.getTargetColumn());
            schema.setJoinCondition(relation.getJoinCondition());
            schema.setFetchStrategy(relation.getFetchStrategy());
            schema.setUi(relation.getUi());

            List<String> synonyms = relation.getSynonyms() == null ? Collections.emptyList() : relation.getSynonyms().stream()
                    .map(OntologyRelationSynonym::getValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            schema.setSynonyms(new ArrayList<>(synonyms));

            relations.put(relation.getName(), schema);
        }
        return relations;
    }

    private void persistOntology(OntologyDto ontologyDto) {
        ontologyRelationRepository.deleteAll();
        ontologyFieldRepository.deleteAll();
        ontologyEntityRepository.deleteAll();

        if (ontologyDto == null || ontologyDto.getEntities() == null || ontologyDto.getEntities().isEmpty()) {
            return;
        }

        Map<String, OntologyEntity> entities = new LinkedHashMap<>();
        ontologyDto.getEntities().forEach((entityName, schema) -> {
            OntologyEntity entity = new OntologyEntity();
            entity.setName(entityName);
            applyMeta(entity, schema.getMeta());
            entities.put(entityName, entity);
        });

        ontologyDto.getEntities().forEach((entityName, schema) -> {
            OntologyEntity entity = entities.get(entityName);
            populateFields(entity, schema.getFields());
            populateRelations(entity, schema.getRelations(), entities);
        });

        ontologyEntityRepository.saveAll(entities.values());
    }

    private void applyMeta(OntologyEntity entity, OntologyDto.Meta meta) {
        if (meta == null) {
            entity.setSynonyms(new ArrayList<>());
            entity.setPermissionRead(false);
            entity.setPermissionWrite(false);
            return;
        }

        entity.setUserFriendlyName(meta.getUserFriendlyName());
        entity.setUserFriendlyNameAccusative(meta.getUserFriendlyNameAccusative());
        entity.setUserFriendlyNamePlural(meta.getUserFriendlyNamePlural());
        entity.setUserFriendlyNamePluralGenitive(meta.getUserFriendlyNamePluralGenitive());
        entity.setEntityNamePlural(meta.getEntityNamePlural());
        entity.setDescription(meta.getDescription());
        entity.setPrimaryTable(meta.getPrimaryTable());
        entity.setDefaultSearchField(meta.getDefaultSearchField());
        entity.setSynonyms(new ArrayList<>(Optional.ofNullable(meta.getSynonyms()).orElse(Collections.emptyList())));

        OntologyDto.Permissions permissions = meta.getPermissions();
        entity.setPermissionRead(permissions != null && permissions.isRead());
        entity.setPermissionWrite(permissions != null && permissions.isWrite());
    }

    private void populateFields(OntologyEntity entity, List<OntologyDto.EntitySchema.FieldSchema> fields) {
        entity.getFields().clear();
        if (fields == null) {
            return;
        }
        for (OntologyDto.EntitySchema.FieldSchema fieldSchema : fields) {
            OntologyField field = new OntologyField();
            field.setEntity(entity);
            field.setName(fieldSchema.getName());
            field.setType(fieldSchema.getType());
            field.setDescription(fieldSchema.getDescription());
            field.setUserFriendlyName(fieldSchema.getUserFriendlyName());
            field.setUi(fieldSchema.getUi());

            FieldDbMetadata metadata = field.getDbMetadata();
            if (metadata == null) {
                metadata = new FieldDbMetadata();
                field.setDbMetadata(metadata);
            }

            OntologyDto.EntitySchema.FieldSchema.DbInfo dbInfo = fieldSchema.getDb();
            if (dbInfo != null) {
                metadata.setTableName(dbInfo.getTable());
                metadata.setColumnName(dbInfo.getColumn());
                metadata.setPrimaryKey(Boolean.TRUE.equals(dbInfo.getIsPrimaryKey()));
                metadata.setRelationName(dbInfo.getRelationName());
            } else {
                metadata.setTableName(null);
                metadata.setColumnName(null);
                metadata.setPrimaryKey(false);
                metadata.setRelationName(null);
            }

            List<String> synonyms = Optional.ofNullable(fieldSchema.getSynonyms()).orElse(Collections.emptyList());
            for (String synonymValue : synonyms) {
                if (synonymValue == null) {
                    continue;
                }
                OntologyFieldSynonym synonym = new OntologyFieldSynonym();
                synonym.setField(field);
                synonym.setValue(synonymValue);
                field.getSynonyms().add(synonym);
            }

            OntologyDto.Permissions permissions = fieldSchema.getPermissions();
            if (permissions != null) {
                OntologyFieldPermission permission = new OntologyFieldPermission();
                permission.setField(field);
                permission.setCanRead(permissions.isRead());
                permission.setCanWrite(permissions.isWrite());
                field.setPermission(permission);
            }

            entity.getFields().add(field);
        }
    }

    private void populateRelations(OntologyEntity entity,
                                   Map<String, OntologyDto.EntitySchema.RelationSchema> relations,
                                   Map<String, OntologyEntity> entities) {
        entity.getRelations().clear();
        if (relations == null || relations.isEmpty()) {
            return;
        }
        relations.forEach((relationName, relationSchema) -> {
            OntologyRelation relation = new OntologyRelation();
            relation.setSourceEntity(entity);
            relation.setName(relationName);
            relation.setType(relationSchema.getType());
            relation.setTargetEntityName(relationSchema.getTargetEntity());
            relation.setTargetEntity(entities.get(relationSchema.getTargetEntity()));
            relation.setSourceTable(relationSchema.getSourceTable());
            relation.setSourceColumn(relationSchema.getSourceColumn());
            relation.setTargetTable(relationSchema.getTargetTable());
            relation.setTargetColumn(relationSchema.getTargetColumn());
            relation.setJoinCondition(relationSchema.getJoinCondition());
            relation.setFetchStrategy(relationSchema.getFetchStrategy());
            relation.setUi(relationSchema.getUi());

            List<String> synonyms = Optional.ofNullable(relationSchema.getSynonyms()).orElse(Collections.emptyList());
            for (String synonymValue : synonyms) {
                if (synonymValue == null) {
                    continue;
                }
                OntologyRelationSynonym synonym = new OntologyRelationSynonym();
                synonym.setRelation(relation);
                synonym.setValue(synonymValue);
                relation.getSynonyms().add(synonym);
            }

            entity.getRelations().add(relation);
        });
    }

    public OntologyDto.EntitySchema getEntitySchema(String entityName) {
        OntologyDto dto = getOntologyFromDb();
        if (dto == null || !dto.getEntities().containsKey(entityName)) {
            throw new ResourceNotFoundException("Схема для сущности '" + entityName + "' не найдена.");
        }
        return dto.getEntities().get(entityName);
    }

    public List<OntologyDto.EntitySchema.FieldSchema> getFieldsForEntity(String entityName) {
        OntologyDto.EntitySchema entitySchema = getEntitySchema(entityName);
        List<OntologyDto.EntitySchema.FieldSchema> fields = entitySchema.getFields();

        if (fields == null) {
            log.warn("Для сущности '{}' в онтологии не определен список полей 'fields'. Возвращается пустой список.", entityName);
            return Collections.emptyList();
        }

        return fields;
    }


    public OntologyDto getCurrentOntologySchema() {
        return getOntologyFromDb();
    }

    @Transactional
    public OntologyDto updateOntology(OntologyDto newOntologyDtoSchema) {
        persistOntology(newOntologyDtoSchema);

        Ontology storage = ontologyRepository.findFirstByOrderByCreationDateDesc()
                .orElseGet(Ontology::new);
        storage.setSchema(newOntologyDtoSchema);
        ontologyRepository.save(storage);

        return getOntologyFromDb();
    }

    public EntityMetaData getEntityMetaData(String entityName) {
        OntologyDto.EntitySchema entitySchema = getEntitySchema(entityName);
        OntologyDto.Meta meta = Optional.ofNullable(entitySchema.getMeta())
                .orElse(new OntologyDto.Meta());

        String userFriendlyName = Optional.ofNullable(meta.getUserFriendlyName()).orElse("Элемент");
        String userFriendlyNamePlural = Optional.ofNullable(meta.getUserFriendlyNamePlural()).orElse("Элементы");
        String userFriendlyNameAccusative = Optional.ofNullable(meta.getUserFriendlyNameAccusative()).orElse(userFriendlyName);
        String userFriendlyNamePluralGenitive = Optional.ofNullable(meta.getUserFriendlyNamePluralGenitive())
                .orElse(userFriendlyNamePlural.toLowerCase(Locale.ROOT));

        OntologyDto.Permissions entityPermissions = Optional.ofNullable(meta.getPermissions())
                .orElse(new OntologyDto.Permissions());

        return EntityMetaData.builder()
                .entityName(entityName)
                .viewTitle(userFriendlyNamePlural)
                .viewDescription(Optional.ofNullable(meta.getDescription()).orElse(""))
                .userFriendlyName(userFriendlyName)
                .userFriendlyNameAccusative(userFriendlyNameAccusative)
                .userFriendlyNameAccusativeLowercase(userFriendlyNameAccusative.toLowerCase(Locale.ROOT))
                .userFriendlyNamePlural(userFriendlyNamePlural)
                .userFriendlyNamePluralGenitive(userFriendlyNamePluralGenitive)
                .userFriendlyNameLowercase(userFriendlyName.toLowerCase(Locale.ROOT))
                .permissions(entityPermissions)
                .defaultSearchField(meta.getDefaultSearchField())
                .synonyms(Optional.ofNullable(meta.getSynonyms()).orElse(Collections.emptyList()))
                .build();
    }

    public List<String> getSearchableFieldsForUI(String entityName) {
        List<OntologyDto.EntitySchema.FieldSchema> fields = getFieldsForEntity(entityName);
        if (fields.isEmpty()) {
            return Collections.emptyList();
        }
        return fields.stream()
                .filter(field -> field.getUi() != null &&
                        field.getUi().getListApplet() != null &&
                        field.getUi().getListApplet().isSearchable())
                .map(OntologyDto.EntitySchema.FieldSchema::getName)
                .collect(Collectors.toList());
    }

    public List<OntologyDto.EntitySchema.FieldSchema> getAllQueryableFields() {
        OntologyDto ontologyDto = getOntologyFromDb();
        if (ontologyDto == null || ontologyDto.getEntities() == null) {
            return Collections.emptyList();
        }

        return ontologyDto.getEntities().values().stream()
                .flatMap(entitySchema -> Optional.ofNullable(entitySchema.getFields()).orElse(Collections.emptyList()).stream())
                .filter(field -> field.getUi() != null && field.getUi().isQueryable())
                .collect(Collectors.toList());
    }

    public Map<String, EntityMetaData> getAllEntityMetaForPrompt() {
        OntologyDto ontologyDto = getOntologyFromDb();
        if (ontologyDto == null || ontologyDto.getEntities() == null) {
            return Collections.emptyMap();
        }

        return ontologyDto.getEntities().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            OntologyDto.Meta meta = Optional.ofNullable(entry.getValue().getMeta()).orElse(new OntologyDto.Meta());
                            String userFriendlyName = Optional.ofNullable(meta.getUserFriendlyName()).orElse("Элемент");
                            List<String> synonyms = Optional.ofNullable(meta.getSynonyms()).orElse(Collections.emptyList());

                            return EntityMetaData.builder()
                                    .entityName(entry.getKey())
                                    .userFriendlyName(userFriendlyName)
                                    .synonyms(synonyms)
                                    .build();
                        }
                ));
    }

    public boolean entityExists(String entityName) {
        if (entityName == null) {
            return false;
        }
        return getOntologyFromDb().getEntities().containsKey(entityName);
    }


    @Transactional
    public OntologyDto syncOntologyFromConfluence() {
        log.info("Запуск синхронизации онтологии из Confluence (pageId: {})", ontologyPageId);

        ConfluencePageResponse confluenceResponse;
        try {
            confluenceResponse = confluenceClient.getPageContent(ontologyPageId);
            log.debug("Получен ответ от Confluence API для страницы {}.", ontologyPageId);
        } catch (FeignException e) {
            log.error("Ошибка при запросе к Confluence API (pageId: {}): {} - {}", ontologyPageId, e.status(), e.getMessage());
            throw new RuntimeException("Не удалось получить онтологию из Confluence: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при запросе к Confluence API (pageId: {}): {}", ontologyPageId, e.getMessage(), e);
            throw new RuntimeException("Не удалось получить онтологию из Confluence: " + e.getMessage(), e);
        }


        if (confluenceResponse == null || confluenceResponse.getBody() == null ||
                confluenceResponse.getBody().getStorage() == null ||
                confluenceResponse.getBody().getStorage().getValue() == null) {
            log.error("Не удалось получить содержимое страницы Confluence или оно пустое для pageId: {}", ontologyPageId);
            throw new ResourceNotFoundException("Содержимое страницы онтологии в Confluence не найдено или пусто.");
        }

        String htmlContent = confluenceResponse.getBody().getStorage().getValue();
        log.debug("Получено HTML-содержимое страницы Confluence. Длина: {}", htmlContent.length());

        String ontologyJsonString = extractJsonFromConfluenceHtml(htmlContent);
        if (ontologyJsonString == null || ontologyJsonString.isBlank()) {
            log.error("Не удалось извлечь JSON-строку онтологии из HTML-контента страницы Confluence.");
            throw new IllegalArgumentException("JSON-строка онтологии не найдена или пуста в Confluence. Убедитесь, что она находится внутри макроса 'Code Block'.");
        }
        log.debug("Извлечена JSON-строка онтологии. Длина: {}", ontologyJsonString.length());

        OntologyDto newOntologyDto;
        try {
            newOntologyDto = objectMapper.readValue(ontologyJsonString, OntologyDto.class);
            log.info("JSON онтологии успешно десериализован в OntologyDto.");
        } catch (JsonProcessingException e) {
            log.error("Ошибка при десериализации JSON онтологии из Confluence: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Некорректный JSON формат онтологии: " + e.getMessage(), e);
        }

        OntologyDto updatedOntology = updateOntology(newOntologyDto);
        log.info("Онтология успешно обновлена в базе данных.");

        return updatedOntology;
    }

    /**
     * Извлекает JSON-строку онтологии из HTML-содержимого страницы Confluence.
     * Ожидается, что JSON находится внутри <ac:structured-macro ac:name="code">
     * с <ac:plain-text-body> и CDATA.
     *
     * @param htmlContent HTML-содержимое страницы Confluence.
     * @return Извлеченная JSON-строка или null, если не найдена.
     */
    private String extractJsonFromConfluenceHtml(String htmlContent) {
        Document doc = Jsoup.parse(htmlContent);

        Element codeBody = doc.selectFirst("ac\\:structured-macro[ac\\:name=code] ac\\:plain-text-body");

        if (codeBody == null) {

            for (Element element : doc.select("ac\\:plain-text-body")) {
                Element parent = element.parent();
                if (parent != null &&
                        parent.tagName().equals("ac:structured-macro") &&
                        "code".equals(parent.attr("ac:name"))) {
                    codeBody = element;
                    break;
                }
            }
        }

        if (codeBody != null) {
            String jsonContent = codeBody.text();
            return jsonContent.trim();
        } else {
            log.warn("Не удалось найти макрос 'code' с онтологией на странице Confluence, даже после попыток с запасными селекторами. Проверьте HTML-разметку.");
            return null;
        }
    }
}