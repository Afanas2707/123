package org.nobilis.nobichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.entities.EntitiesSearchRequestDto;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.dto.ontology.EntityMetaData;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.model.ChatMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicViewGeneratorService {

    private final OntologyService ontologyService;
    private final ObjectMapper objectMapper;
    private final LlmPromptService llmPromptService; // Новая зависимость вместо прямой LLMService и ApplicationEventPublisher
    private final UiComponentService uiComponentService;

    private static final int MAX_ATTEMPTS = 3;
    private static final String LIST_VIEW_SKELETON_PATH = "list-view-skeleton.json";
    private static final String FORM_VIEW_SKELETON_PATH = "form-view-skeleton.json";

    /**
     * Генерирует JSON-схему для ListView в "мягком" режиме.
     * Использует LLM для выбора полей, которые нужно отобразить.
     */
    public JsonNode generateListViewSoft(String entityName, String userQuery, EntitiesSearchRequestDto.QueryDto query, ChatMessage chatMessage, Pageable pageable) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("Генерация UI для '{}' в МЯГКОМ режиме. Попытка {} из {}.", userQuery, attempt, MAX_ATTEMPTS);

                EntityMetaData metaData = ontologyService.getEntityMetaData(entityName);
                List<OntologyDto.EntitySchema.FieldSchema> allFields = ontologyService.getFieldsForEntity(entityName);
                Map<String, OntologyDto.EntitySchema.FieldSchema> fieldsMap = allFields.stream()
                        .collect(Collectors.toMap(OntologyDto.EntitySchema.FieldSchema::getName, Function.identity()));

                List<String> selectedFieldNames = selectFieldsWithLlm(entityName, allFields, userQuery, chatMessage);

                String skeleton = loadResourceFileAsString(LIST_VIEW_SKELETON_PATH);
                List<JsonNode> generatedColumns = generateAndSortColumns(selectedFieldNames, fieldsMap);
                List<String> searchableFieldsForUI = getSearchableFieldsForDisplayedColumns(selectedFieldNames, fieldsMap);


                return buildFinalSchema(skeleton, metaData, searchableFieldsForUI, generatedColumns, query, pageable);

            } catch (IOException | RuntimeException e) {
                log.warn("Попытка #{} не удалась (МЯГКИЙ режим): LLM вернула некорректные данные или произошла ошибка сборки. Ошибка: {}", attempt, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Не удалось сгенерировать валидный UI в МЯГКОМ режиме после {} попыток для запроса: '{}'", MAX_ATTEMPTS, userQuery, e);

                    return null;
                }
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать UI в МЯГКОМ режиме после всех попыток.");
    }

    /**
     * Генерирует JSON-схему для FormView (карточки сущности).
     *
     * @param entityName Имя сущности в онтологии.
     * @param entityId   ID конкретной сущности, для которой генерируется форма.
     * @return JsonNode с UI-схемой формы.
     */
    public JsonNode generateFormView(String entityName, UUID entityId) {
        try {
            log.info("Начало генерации FormView для сущности '{}' с ID: {}", entityName, entityId);

            EntityMetaData metaData = ontologyService.getEntityMetaData(entityName);
            OntologyDto.EntitySchema entitySchema = ontologyService.getEntitySchema(entityName);

            List<OntologyDto.EntitySchema.FieldSchema> allFormFields = entitySchema.getFields().stream()
                    .filter(OntologyDto.EntitySchema.FieldSchema::isDefaultInCard)
                    .collect(Collectors.toList());
            log.info("Найдено {} полей с флагом isDefaultInCard.", allFormFields.size());

            ArrayNode cardLayout = buildCardLayout(allFormFields);
            ArrayNode controlsLayout = buildControlsLayout(allFormFields);
            ArrayNode detailViewsLayout = buildDetailViewsLayout(entityName, entitySchema, allFormFields);
            ArrayNode formActions = buildFormActions(metaData);
            ArrayNode fieldsForSource = buildFieldsForSource(allFormFields);

            ObjectNode rootNode = (ObjectNode) objectMapper.readTree(loadResourceFileAsString(FORM_VIEW_SKELETON_PATH));

            ObjectNode viewNode = (ObjectNode) rootNode.path("view");
            viewNode.put("id", entityName + ".edit.form");
            viewNode.put("name", "Карточка " + metaData.getUserFriendlyNameAccusativeLowercase());
            viewNode.put("entity", entityName);
            viewNode.set("permissions", objectMapper.valueToTree(metaData.getPermissions()));
            viewNode.put("sourceId", entityId.toString());

            ObjectNode appletNode = (ObjectNode) viewNode.at("/applets/0");
            appletNode.put("title", "{name}");
            appletNode.put("description", metaData.getViewDescription());

            appletNode.set("card", cardLayout);
            appletNode.set("controls", controlsLayout);
            appletNode.set("detailViews", detailViewsLayout);
            appletNode.set("actions", formActions);

            ((ObjectNode) appletNode.at("/source/body")).set("fields", fieldsForSource);

            if (rootNode.has("suggestedPrompts")) {
                rootNode.remove("suggestedPrompts");
            }
            if (viewNode.has("previousView")) {
                viewNode.put("previousView", entityName + ".list.view");
            }

            log.info("Генерация FormView успешно завершена.");
            return rootNode;

        } catch (IOException e) {
            log.error("Критическая ошибка при генерации FormView для '{}'", entityName, e);
            throw new RuntimeException("Ошибка при генерации FormView.", e);
        }
    }

    private ArrayNode buildCardLayout(List<OntologyDto.EntitySchema.FieldSchema> allFormFields) {
        ArrayNode cardLayout = objectMapper.createArrayNode();
        allFormFields.stream()
                .map(this::toFormFieldView)
                .flatMap(Optional::stream)
                .filter(view -> view.config().path("isCardHeader").asBoolean(false))
                .sorted(Comparator.comparingInt(view -> view.config().path("displaySequence").asInt(Integer.MAX_VALUE)))
                .forEach(view -> cardLayout.add(buildControlItemForForm(view)));
        return cardLayout;
    }

    private ArrayNode buildControlsLayout(List<OntologyDto.EntitySchema.FieldSchema> allFormFields) {
        ArrayNode controlsLayout = objectMapper.createArrayNode();
        allFormFields.stream()
                .map(this::toFormFieldView)
                .flatMap(Optional::stream)
                .filter(view -> view.config().path("isControl").asBoolean(false)
                        && "Основная информация".equalsIgnoreCase(view.config().path("section").asText("")))
                .sorted(Comparator.comparingInt(view -> view.config().path("displaySequence").asInt(Integer.MAX_VALUE)))
                .forEach(view -> controlsLayout.add(buildControlItemForForm(view)));
        return controlsLayout;
    }

    private ArrayNode buildDetailViewsLayout(String rootEntityName, OntologyDto.EntitySchema entitySchema, List<OntologyDto.EntitySchema.FieldSchema> allFormFields) {
        List<JsonNode> detailViews = new ArrayList<>();

        Map<String, List<FormFieldView>> fieldsBySection = allFormFields.stream()
                .map(this::toFormFieldView)
                .flatMap(Optional::stream)
                .filter(view -> {
                    String section = view.config().path("section").asText(null);
                    return section != null
                            && !section.equalsIgnoreCase("Основная информация")
                            && !section.equalsIgnoreCase("Card Header");
                })
                .collect(Collectors.groupingBy(view -> view.config().path("section").asText()));

        fieldsBySection.forEach((sectionName, views) -> {
            views.sort(Comparator.comparingInt(view -> view.config().path("displaySequence").asInt(Integer.MAX_VALUE)));
            detailViews.add(buildFormAppletDetailView(sectionName, rootEntityName, views));
        });

        if (entitySchema.getRelations() != null) {
            entitySchema.getRelations().values().stream()
                    .filter(OntologyDto.EntitySchema.RelationSchema::isDefaultInCard)
                    .forEach(relation -> detailViews.add(buildListAppletDetailView(relation, rootEntityName)));
        }

        detailViews.sort(Comparator.comparingInt(node -> node.path("displaySequence").asInt(Integer.MAX_VALUE)));

        return objectMapper.valueToTree(detailViews);
    }

    private ObjectNode buildFormAppletDetailView(String sectionName, String rootEntityNameSingular, List<FormFieldView> fields) {
        ObjectNode detailView = objectMapper.createObjectNode();
        detailView.put("label", sectionName);

        fields.stream().findFirst()
                .ifPresent(view -> detailView.put("displaySequence", view.config().path("displaySequence").asInt(0)));

        ObjectNode applet = objectMapper.createObjectNode();
        applet.put("type", "FormApplet");
        applet.put("title", sectionName);
        applet.put("entity", rootEntityNameSingular);

        ObjectNode source = objectMapper.createObjectNode();
        source.put("method", "POST");
        source.put("endpoint", "/entities/{view.entity}/{view.sourceId}");

        ArrayNode fieldsForSource = objectMapper.createArrayNode();
        fields.forEach(f -> fieldsForSource.add(f.field().getName()));
        ObjectNode body = objectMapper.createObjectNode();
        body.set("fields", fieldsForSource);
        source.set("body", body);
        applet.set("source", source);

        ArrayNode controls = objectMapper.createArrayNode();
        fields.stream()
                .sorted(Comparator.comparingInt(view -> view.config().path("displaySequence").asInt(Integer.MAX_VALUE)))
                .forEach(view -> controls.add(buildControlItemForForm(view)));
        applet.set("controls", controls);

        detailView.set("applet", applet);
        return detailView;
    }

    private ObjectNode buildListAppletDetailView(OntologyDto.EntitySchema.RelationSchema relation, String rootEntityNameSingular) {
        ObjectNode detailView = objectMapper.createObjectNode();

        ObjectNode applet = objectMapper.createObjectNode();
        uiComponentService.getConfig(relation.getComponentId())
                .filter(JsonNode::isObject)
                .map(JsonNode::deepCopy)
                .map(ObjectNode.class::cast)
                .ifPresent(applet::setAll);

        String label = relation.getLabel() != null ? relation.getLabel() : applet.path("title").asText(relation.getTargetEntity());
        detailView.put("label", label);
        detailView.put("displaySequence", relation.getDisplaySequence());

        applet.put("type", applet.path("type").asText("ListApplet"));
        applet.put("title", applet.path("title").asText(label));
        applet.put("entity", relation.getTargetEntity());

        applet.set("source", buildListAppletSource(rootEntityNameSingular, relation.getTargetEntity()));

        OntologyDto.EntitySchema targetSchema = ontologyService.getEntitySchema(relation.getTargetEntity());
        applet.set("listColumns", buildListAppletColumns(targetSchema));

        applet.set("controls", objectMapper.createArrayNode());
        ObjectNode footerText = objectMapper.createObjectNode();
        footerText.put("type", "templateText");
        footerText.put("label", "Всего: {totalElements}");
        applet.set("footerText", footerText);

        detailView.set("applet", applet);
        return detailView;
    }

    private ObjectNode buildListAppletSource(String rootEntityNameSingular, String targetEntityName) {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("method", "POST");
        source.put("endpoint", "/entities/{entity}/search");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", "page");
        body.put("perPage", "perPage");
        body.set("fields", objectMapper.createArrayNode());

        ObjectNode query = objectMapper.createObjectNode();
        ArrayNode conditions = objectMapper.createArrayNode();
        ObjectNode condition = objectMapper.createObjectNode();

        condition.put("field", rootEntityNameSingular + ".id");
        condition.put("value", "{view.sourceId}");
        condition.put("operator", "equals");

        conditions.add(condition);
        query.set("conditions", conditions);
        body.set("query", query);
        source.set("body", body);

        ObjectNode search = objectMapper.createObjectNode();
        search.put("debounce", 300);
        search.put("placeholder", "Поиск");

        OntologyDto.EntitySchema targetSchema = ontologyService.getEntitySchema(targetEntityName);
        ArrayNode searchFields = objectMapper.createArrayNode();
        targetSchema.getFields().stream()
                .filter(this::isFieldSearchableInList)
                .map(OntologyDto.EntitySchema.FieldSchema::getName)
                .forEach(searchFields::add);
        search.set("searchFields", searchFields);

        source.set("search", search);

        return source;
    }

    private ArrayNode buildListAppletColumns(OntologyDto.EntitySchema targetSchema) {
        ArrayNode columns = objectMapper.createArrayNode();
        if (targetSchema.getFields() == null) return columns;

        targetSchema.getFields().stream()
                .map(this::toListFieldView)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(view -> view.config().path("displaySequence").asInt(Integer.MAX_VALUE)))
                .forEach(view -> columns.add(buildListColumnNode(view)));
        return columns;
    }

    private ArrayNode buildFormActions(EntityMetaData metaData) {
        ArrayNode actions = objectMapper.createArrayNode();
        if (metaData.getPermissions() != null && metaData.getPermissions().isWrite()) {
            ObjectNode saveAction = objectMapper.createObjectNode();
            saveAction.put("name", "save");
            saveAction.put("type", "button");
            saveAction.put("label", "Сохранить данные");
            saveAction.put("variant", "primary");
            saveAction.set("permissions", objectMapper.valueToTree(metaData.getPermissions()));

            ObjectNode action = objectMapper.createObjectNode();
            action.put("type", "api");
            action.put("method", "PATCH");
            action.put("endpoint", "/entities/{view.entity}/{id}");
            saveAction.set("action", action);

            actions.add(saveAction);
        }
        return actions;
    }

    private ObjectNode buildControlItemForForm(FormFieldView view) {
        ObjectNode item = objectMapper.createObjectNode();
        OntologyDto.EntitySchema.FieldSchema field = view.field();
        JsonNode formView = view.config();

        item.put("name", field.getName());
        String label = formView.path("label").asText(field.getUserFriendlyName() != null
                ? field.getUserFriendlyName()
                : field.getName());
        item.put("label", label);
        item.set("component", buildComponentNodeForForm(view));
        item.put("displaySequence", formView.path("displaySequence").asInt(0));

        return item;
    }

    private ObjectNode buildComponentNodeForForm(FormFieldView view) {
        ObjectNode component = objectMapper.createObjectNode();
        OntologyDto.EntitySchema.FieldSchema field = view.field();
        JsonNode formView = view.config();

        component.put("type", formView.path("component").asText());
        String dataType = formView.path("dataType").asText(null);
        component.put("dataType", dataType != null ? dataType : field.getType());
        component.put("fieldName", field.getName());
        if (formView.path("required").asBoolean(false)) {
            component.put("required", true);
        }
        if (formView.path("disabled").asBoolean(false)) {
            component.put("disabled", true);
        }
        if (formView.has("action") && !formView.get("action").isNull()) {
            component.set("action", formView.get("action"));
        }
        if (formView.has("components") && formView.get("components").isArray() && formView.get("components").size() > 0) {
            component.set("components", formView.get("components"));
        }
        return component;
    }

    private ArrayNode buildFieldsForSource(List<OntologyDto.EntitySchema.FieldSchema> allFormFields) {
        ArrayNode fieldsForSource = objectMapper.createArrayNode();
        allFormFields.forEach(f -> fieldsForSource.add(f.getName()));
        return fieldsForSource;
    }

    /**
     * Генерирует JSON-схему для ListView в "строгом" режиме с 3 попытками.
     * Выбирает поля на основе флага isDefaultInList в онтологии.
     */
    public JsonNode generateListViewStrict(String entityName, EntitiesSearchRequestDto.QueryDto query, Pageable pageable) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("Генерация UI для сущности '{}' в СТРОГОМ режиме. Попытка {} из {}.", entityName, attempt, MAX_ATTEMPTS);

                EntityMetaData metaData = ontologyService.getEntityMetaData(entityName);
                List<OntologyDto.EntitySchema.FieldSchema> allFields = ontologyService.getFieldsForEntity(entityName);
                Map<String, OntologyDto.EntitySchema.FieldSchema> fieldsMap = allFields.stream()
                        .collect(Collectors.toMap(OntologyDto.EntitySchema.FieldSchema::getName, Function.identity()));

                List<String> selectedFieldNames = allFields.stream()
                        .filter(OntologyDto.EntitySchema.FieldSchema::isDefaultInList)
                        .map(OntologyDto.EntitySchema.FieldSchema::getName)
                        .collect(Collectors.toList());

                log.info("Выбраны поля по умолчанию для строгого режима: {}", selectedFieldNames);

                String skeleton = loadResourceFileAsString(LIST_VIEW_SKELETON_PATH);
                List<JsonNode> generatedColumns = generateAndSortColumns(selectedFieldNames, fieldsMap);
                List<String> searchableFieldsForUI = getSearchableFieldsForDisplayedColumns(selectedFieldNames, fieldsMap);

                return buildFinalSchema(skeleton, metaData, searchableFieldsForUI, generatedColumns, query, pageable);

            } catch (IOException | RuntimeException e) {
                log.warn("Попытка #{} не удалась (СТРОГИЙ режим): Произошла ошибка сборки UI. Ошибка: {}", attempt, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Не удалось сгенерировать валидный UI в СТРОГОМ режиме после {} попыток для сущности: '{}'", MAX_ATTEMPTS, entityName, e);
                    return null;
                }
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать UI в СТРОГОМ режиме после всех попыток.");
    }

    private List<String> selectFieldsWithLlm(String entityName, List<OntologyDto.EntitySchema.FieldSchema> allFields, String userQuery, ChatMessage chatMessage) throws JsonProcessingException {
        String prompt = llmPromptService.buildFieldNameSelectionPrompt(entityName, allFields, userQuery);
        LLMResponseDto llmResponse = llmPromptService.sendPromptToLlm(prompt, null, true, chatMessage.getId());

        String rawContent = llmResponse.getContent();
        String cleanJson = llmPromptService.sanitizeLlmJsonResponse(rawContent);

        return objectMapper.readValue(cleanJson, new TypeReference<List<String>>() {
        });
    }

    private List<JsonNode> generateAndSortColumns(List<String> selectedFieldNames, Map<String, OntologyDto.EntitySchema.FieldSchema> fieldsMap) {
        return selectedFieldNames.stream()
                .map(fieldsMap::get)
                .filter(Objects::nonNull)
                .map(this::toListFieldView)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(view -> view.config().path("displaySequence").asInt(Integer.MAX_VALUE)))
                .map(view -> (JsonNode) buildListColumnNode(view))
                .collect(Collectors.toList());
    }

    private List<String> getSearchableFieldsForDisplayedColumns(List<String> selectedFieldNames, Map<String, OntologyDto.EntitySchema.FieldSchema> fieldsMap) {
        return selectedFieldNames.stream()
                .map(fieldsMap::get)
                .filter(Objects::nonNull)
                .filter(this::isFieldSearchableInList)
                .map(OntologyDto.EntitySchema.FieldSchema::getName)
                .collect(Collectors.toList());
    }


    /**
     * Собирает финальную схему, используя надежный метод работы с JSON-деревом.
     *
     * @param skeleton              Исходный JSON-скелет.
     * @param meta                  Метаданные сущности.
     * @param searchableFieldsForUI Список полей для UI поиска.
     * @param generatedColumns      Сгенерированные колонки для списка.
     * @return Финальная JSON-схема.
     * @throws IOException Если произошла ошибка при работе с JSON.
     */
    private JsonNode buildFinalSchema(
            String skeleton,
            EntityMetaData meta,
            List<String> searchableFieldsForUI,
            List<JsonNode> generatedColumns,
            EntitiesSearchRequestDto.QueryDto query,
            Pageable pageable) throws IOException {

        String populatedSkeleton = skeleton
                .replace("%%ENTITY_NAME%%", meta.getEntityName())
                .replace("%%ENTITY_USER_FRIENDLY_NAME_PLURAL_LOWERCASE%%", meta.getUserFriendlyNamePluralGenitive())
                .replace("%%APPLET_TITLE%%", meta.getUserFriendlyNamePlural())
                .replace("%%VIEW_DESCRIPTION%%", meta.getViewDescription())
                .replace("%%ENTITY_USER_FRIENDLY_NAME_ACCUSATIVE%%", meta.getUserFriendlyNameAccusative())
                .replace("%%ENTITY_USER_FRIENDLY_NAME_LOWERCASE%%", meta.getUserFriendlyNameLowercase())
                .replace("%%ENTITY_USER_FRIENDLY_NAME_ACCUSATIVE_LOWERCASE%%", meta.getUserFriendlyNameAccusativeLowercase());

        ObjectNode rootNode = (ObjectNode) objectMapper.readTree(populatedSkeleton);
        ObjectNode viewNode = (ObjectNode) rootNode.path("view");
        ObjectNode appletNode = (ObjectNode) viewNode.at("/applets/0");
        ObjectNode sourceNode = (ObjectNode) appletNode.path("source");
        ObjectNode bodyNode = (ObjectNode) sourceNode.path("body"); // Получаем узел 'body'


        if (meta.getPermissions() != null) {
            viewNode.set("permissions", objectMapper.valueToTree(meta.getPermissions()));
        } else {
            viewNode.set("permissions", objectMapper.valueToTree(new OntologyDto.Permissions()));
        }

        ArrayNode searchFieldsNode = objectMapper.createArrayNode();
        searchableFieldsForUI.forEach(searchFieldsNode::add);

        ObjectNode searchNode = (ObjectNode) rootNode.at("/view/applets/0/source/search");
        if (!searchNode.isMissingNode()) {
            searchNode.set("searchFields", searchFieldsNode);
        }

        if (query != null) {
            bodyNode.set("query", objectMapper.valueToTree(query));
        }

        // НОВОЕ: Добавляем информацию о сортировке в sourceNode
        if (pageable != null && pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().stream().findFirst().orElse(null); // Берем первый порядок сортировки
            if (order != null) {
                sourceNode.put("sortBy", order.getProperty());
                sourceNode.put("sortOrder", order.getDirection().name());
            }
        }

        ArrayNode listColumnsNode = (ArrayNode) rootNode.at("/view/applets/0/listColumns");
        if (!listColumnsNode.isMissingNode()) {
            int placeholderIndex = -1;
            for (int i = 0; i < listColumnsNode.size(); i++) {
                if (listColumnsNode.get(i).isTextual() && "%%GENERATED_COLUMNS_PLACEHOLDER%%".equals(listColumnsNode.get(i).asText())) {
                    placeholderIndex = i;
                    break;
                }
            }

            if (placeholderIndex != -1) {
                listColumnsNode.remove(placeholderIndex);
                for (int i = generatedColumns.size() - 1; i >= 0; i--) {
                    listColumnsNode.insert(placeholderIndex, generatedColumns.get(i));
                }
            }
        }

        ArrayNode actionsNode = (ArrayNode) rootNode.at("/view/applets/0/actions");
        if (!actionsNode.isMissingNode() && meta.getPermissions() != null) {
            for (JsonNode action : actionsNode) {
                if (action instanceof ObjectNode) {
                    ((ObjectNode) action).set("permissions", objectMapper.valueToTree(meta.getPermissions()));
                }
            }
        } else if (!actionsNode.isMissingNode() && meta.getPermissions() == null) {
            for (JsonNode action : actionsNode) {
                if (action instanceof ObjectNode) {
                    ((ObjectNode) action).set("permissions", objectMapper.valueToTree(new OntologyDto.Permissions()));
                }
            }
        }

        return rootNode;
    }

    private Optional<FormFieldView> toFormFieldView(OntologyDto.EntitySchema.FieldSchema field) {
        return uiComponentService.getConfig(field.getFormComponentId())
                .map(config -> new FormFieldView(field, config));
    }

    private Optional<ListFieldView> toListFieldView(OntologyDto.EntitySchema.FieldSchema field) {
        return uiComponentService.getConfig(field.getListComponentId())
                .map(config -> new ListFieldView(field, config));
    }

    private boolean isFieldSearchableInList(OntologyDto.EntitySchema.FieldSchema field) {
        return uiComponentService.getConfig(field.getListComponentId())
                .map(config -> config.path("isSearchable").asBoolean(false))
                .orElse(false);
    }

    private ObjectNode buildListColumnNode(ListFieldView view) {
        ObjectNode columnNode = objectMapper.createObjectNode();
        JsonNode config = view.config();
        String name = config.path("name").asText(view.field().getName());
        columnNode.put("name", name);
        String label = config.path("label").asText(view.field().getUserFriendlyName() != null
                ? view.field().getUserFriendlyName()
                : name);
        columnNode.put("label", label);
        columnNode.put("displaySequence", config.path("displaySequence").asInt(0));
        columnNode.put("isSearchable", config.path("isSearchable").asBoolean(false));

        JsonNode componentNode = config.path("component");
        if (componentNode.isObject()) {
            ObjectNode component = objectMapper.createObjectNode();
            component.setAll((ObjectNode) componentNode);
            if (!component.has("fieldName") || component.get("fieldName").isNull()) {
                component.put("fieldName", view.field().getName());
            }
            if (!component.has("dataType") || component.get("dataType").isNull()) {
                component.put("dataType", view.field().getType());
            }
            columnNode.set("component", component);
        }
        return columnNode;
    }

    private record FormFieldView(OntologyDto.EntitySchema.FieldSchema field, JsonNode config) {
    }

    private record ListFieldView(OntologyDto.EntitySchema.FieldSchema field, JsonNode config) {
    }

    private String loadResourceFileAsString(String path) {
        try (var reader = new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            log.error("Ошибка при загрузке ресурса: {}", path, e);
            throw new RuntimeException("Ошибка при загрузке ресурса: " + path, e);
        }
    }
}