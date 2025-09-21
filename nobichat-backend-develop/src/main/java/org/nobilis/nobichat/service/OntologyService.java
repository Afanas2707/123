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
import org.nobilis.nobichat.repository.OntologyStorageRepository;
import org.springframework.beans.factory.annotation.Value;
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