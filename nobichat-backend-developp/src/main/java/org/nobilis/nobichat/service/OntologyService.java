package org.nobilis.nobichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.constants.OntologyVersion;
import org.nobilis.nobichat.dto.ontology.EntityMetaData;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.model.Ontology;
import org.nobilis.nobichat.repository.OntologyStorageRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyService {

    private final OntologyStorageRepository ontologyRepository;
    private final ObjectMapper objectMapper;

    public Optional<OntologyDto.EntitySchema.FieldSchema> getFieldSchemaOptional(String entityName, String fieldName) {
        try {
            OntologyDto.EntitySchema entitySchema = getEntitySchema(entityName);
            if (entitySchema.getFields() == null) {
                return Optional.empty();
            }
            return entitySchema.getFields().stream()
                    .filter(f -> f.getName().equals(fieldName))
                    .findFirst();
        } catch (ResourceNotFoundException e) {
            log.warn("Попытка получить схему поля '{}' для несуществующей сущности '{}'.", fieldName, entityName);
            return Optional.empty();
        }
    }

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
        return ontologyRepository.findFirstByOrderByCreationDateDesc()
                .map(Ontology::getSchema)
                .orElseThrow(() -> new ResourceNotFoundException("Отсутствует онтология в БД."));
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
        Ontology storage = ontologyRepository.findFirstByOrderByCreationDateDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Отсутствует онтология в БД."));
        storage.setSchema(newOntologyDtoSchema);
        return ontologyRepository.save(storage).getSchema();
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
}