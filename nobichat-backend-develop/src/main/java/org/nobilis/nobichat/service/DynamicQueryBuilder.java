package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.entities.EntitiesSearchRequestDto;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.model.FieldInfo;
import org.nobilis.nobichat.model.QueryContext;
import org.nobilis.nobichat.model.QueryResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicQueryBuilder {

    private final OntologyService ontologyService;

    /**
     * Основной метод, который строит SQL-запрос.
     *
     * @param entityName Имя корневой сущности.
     * @param fields     Список полей для выборки (например, ["name", "orders.orderNumber"]).
     * @param pageable   Объект для пагинации и сортировки.
     * @return Объект QueryResult, содержащий SQL, параметры и метаданные полей.
     */
    public QueryResult build(
            String entityName,
            List<String> fields,
            EntitiesSearchRequestDto.QueryDto query,
            Pageable pageable) {

        OntologyDto.EntitySchema rootSchema = ontologyService.getEntitySchema(entityName);
        QueryContext context = new QueryContext(rootSchema);

        List<String> fieldsToProcess = (fields != null && !fields.isEmpty())
                ? fields
                : rootSchema.getFields().stream().map(OntologyDto.EntitySchema.FieldSchema::getName).collect(Collectors.toList());

        for (String fieldPath : fieldsToProcess) {
            FieldInfo resolvedField = resolvePath(fieldPath, context);
            if (context.getSelectFields().stream().noneMatch(f -> f.getFullPath().equals(resolvedField.getFullPath()))) {
                context.getSelectFields().add(resolvedField);
            }
        }

        if (query != null) {
            String whereContent = processQuery(query, context);
            if (StringUtils.hasText(whereContent)) {
                context.getWhereClauses().add(whereContent);
            }
        }

        String sql = buildSqlStringForList(context, pageable);
        return new QueryResult(sql, context.getQueryParams(), context.getSelectFields());
    }

    /**
     * Строит SQL-запрос для подсчета общего количества записей по сложным критериям.
     *
     * @param entityName Имя корневой сущности.
     * @param query      Объект с условиями и группами для поиска и фильтрации.
     * @return Объект QueryResult, содержащий SQL для COUNT и накопленные параметры.
     */
    public QueryResult buildCount(String entityName, EntitiesSearchRequestDto.QueryDto query) {
        OntologyDto.EntitySchema rootSchema = ontologyService.getEntitySchema(entityName);
        QueryContext context = new QueryContext(rootSchema);

        if (query != null) {
            String whereContent = processQuery(query, context);
            if (StringUtils.hasText(whereContent)) {
                context.getWhereClauses().add(whereContent);
            }
        }

        String fromClause = String.format("FROM %s %s",
                rootSchema.getMeta().getPrimaryTable(),
                context.getRootTableAlias()
        );

        String joinClauses = String.join("\n", context.getJoinClauses().values());

        String whereClause = context.getWhereClauses().isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", context.getWhereClauses());

        String countExpression;
        if (context.getJoinClauses().isEmpty()) {
            countExpression = "COUNT(*)";
        } else {
            String primaryKeyColumn = getPrimaryKeyColumn(rootSchema);
            countExpression = String.format("COUNT(DISTINCT %s.%s)",
                    context.getRootTableAlias(),
                    primaryKeyColumn
            );
        }

        String sql = String.format("SELECT %s %s %s %s",
                countExpression,
                fromClause,
                joinClauses,
                whereClause
        ).trim();

        return new QueryResult(sql, context.getQueryParams(), Collections.emptyList());
    }


    /**
     * Рекурсивно обрабатывает объект QueryDto и строит из него строку для WHERE-клаузы.
     *
     * @param query   Объект с условиями и/или группами.
     * @param context Контекст текущего запроса для накопления параметров и JOIN'ов.
     * @return Готовая строка с условиями для текущего уровня вложенности, например, "((field1 = ?) AND (field2 > ?))".
     */
    private String processQuery(EntitiesSearchRequestDto.QueryDto query, QueryContext context) {
        List<String> allClausesOnThisLevel = new ArrayList<>();

        if (query.getConditions() != null && !query.getConditions().isEmpty()) {
            for (EntitiesSearchRequestDto.QueryDto.ConditionDto condition : query.getConditions()) {
                try {
                    FieldInfo fieldInfo = resolvePath(condition.getField(), context);
                    OntologyDto.EntitySchema.FieldSchema fieldSchema = findFieldByFullPath(fieldInfo.getFullPath(), context.getRootEntitySchema());

                    String paramName = "param_" + fieldInfo.getColumnAlias() + "_" + context.getNextParamName();
                    Object typedValue = convertValueToType(String.valueOf(condition.getValue()), fieldSchema.getType());

                    String clause = switch (condition.getOperator().toLowerCase()) {
                        case "equals" -> String.format("%s.%s = :%s", fieldInfo.getTableAlias(), fieldInfo.getColumnName(), paramName);
                        case "not_equals" -> String.format("%s.%s != :%s", fieldInfo.getTableAlias(), fieldInfo.getColumnName(), paramName);
                        case "contains" -> {
                            context.getQueryParams().put(paramName, "%" + typedValue + "%");
                            yield String.format("CAST(%s.%s AS TEXT) ILIKE :%s", fieldInfo.getTableAlias(), fieldInfo.getColumnName(), paramName);
                        }
                        case "greater_than" -> String.format("%s.%s > :%s", fieldInfo.getTableAlias(), fieldInfo.getColumnName(), paramName);
                        case "less_than" -> String.format("%s.%s < :%s", fieldInfo.getTableAlias(), fieldInfo.getColumnName(), paramName);
                        default -> throw new IllegalArgumentException("Неподдерживаемый оператор: " + condition.getOperator());
                    };

                    if (!"contains".equalsIgnoreCase(condition.getOperator())) {
                        context.getQueryParams().put(paramName, typedValue);
                    }
                    allClausesOnThisLevel.add(clause);

                } catch (Exception e) {
                    throw new IllegalArgumentException("Ошибка при обработке условия для поля '" + condition.getField() + "': " + e.getMessage(), e);
                }
            }
        }

        if (query.getGroups() != null && !query.getGroups().isEmpty()) {
            for (EntitiesSearchRequestDto.QueryDto subQuery : query.getGroups()) {
                String subGroupClause = processQuery(subQuery, context);
                if (StringUtils.hasText(subGroupClause)) {
                    allClausesOnThisLevel.add(subGroupClause);
                }
            }
        }

        if (allClausesOnThisLevel.isEmpty()) {
            return "";
        }

        String operator = " " + ("OR".equalsIgnoreCase(query.getOperator()) ? "OR" : "AND") + " ";
        return "(" + String.join(operator, allClausesOnThisLevel) + ")";
    }

    private String getPrimaryKeyColumn(OntologyDto.EntitySchema schema) {
        return schema.getFields().stream()
                .filter(f -> f.getDb() != null && Boolean.TRUE.equals(f.getDb().getIsPrimaryKey()))
                .findFirst()
                .map(f -> f.getDb().getColumn())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет PK для " + schema.getMeta().getUserFriendlyName()));
    }

    /**
     * Строит SQL-запрос для получения ОДНОЙ сущности.
     * Не использует пагинацию, но добавляет LIMIT 1 для оптимизации.
     *
     * @param entityName Имя корневой сущности.
     * @param fields     Список полей для выборки.
     * @param filters    Карта фильтров (обычно только по ID).
     * @return Объект QueryResult, содержащий SQL, параметры и метаданные полей.
     */
    public QueryResult buildForSingle(String entityName, List<String> fields, Map<String, String> filters) {
        OntologyDto.EntitySchema rootSchema = ontologyService.getEntitySchema(entityName);
        QueryContext context = new QueryContext(rootSchema);

        List<String> fieldsToProcess = (fields != null && !fields.isEmpty())
                ? fields
                : rootSchema.getFields().stream().map(OntologyDto.EntitySchema.FieldSchema::getName).collect(Collectors.toList());

        for (String fieldPath : fieldsToProcess) {
            FieldInfo resolvedField = resolvePath(fieldPath, context);
            if (context.getSelectFields().stream().noneMatch(f -> f.getFullPath().equals(resolvedField.getFullPath()))) {
                context.getSelectFields().add(resolvedField);
            }
        }

        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, String> filterEntry : filters.entrySet()) {
                FieldInfo fieldInfo = resolvePath(filterEntry.getKey(), context);
                processFilter(fieldInfo, filterEntry.getValue(), context);
            }
        }

        String sql = buildSqlStringForSingle(context);

        return new QueryResult(sql, context.getQueryParams(), context.getSelectFields());
    }

    /**
     * Строит SQL-запрос INSERT для создания новой сущности.
     *
     * @param entityName     Имя сущности в онтологии.
     * @param fieldsToCreate Карта полей для создания { "fieldName": "value" }.
     * @return Объект QueryResult, содержащий SQL и параметры.
     */
    public QueryResult buildInsert(String entityName, Map<String, Object> fieldsToCreate) {
        OntologyDto.EntitySchema schema = ontologyService.getEntitySchema(entityName);
        String primaryTable = schema.getMeta().getPrimaryTable();

        List<String> columns = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : fieldsToCreate.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            OntologyDto.EntitySchema.FieldSchema fieldSchema = findFieldInSchema(schema, fieldName);
            if (fieldSchema == null) {
                log.warn("Попытка вставить несуществующее поле '{}' в сущность '{}'.", fieldName, entityName);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Попытка вставить несуществующее поле '" + fieldName + "' в сущность '" + entityName + "'.");
            }

            if (!primaryTable.equals(fieldSchema.getDb().getTable())) {
                log.warn("Попытка вставить поле '{}' из связанной таблицы '{}' через основной эндпоинт сущности '{}'.", fieldName, fieldSchema.getDb().getTable(), entityName);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Попытка вставить поле '" + fieldName + "' из связанной таблицы '" + fieldSchema.getDb().getTable() + "' через основной эндпоинт сущности '" + entityName + "' не разрешена.");
            }

            String paramName = "insert_" + fieldName;
            columns.add(fieldSchema.getDb().getColumn());
            paramNames.add(":" + paramName);

            Object typedValue = convertValueToType(String.valueOf(value), fieldSchema.getType());
            queryParams.put(paramName, typedValue);
        }

        if (columns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет валидных полей для создания сущности.");
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                primaryTable,
                String.join(", ", columns),
                String.join(", ", paramNames)
        );

        return new QueryResult(sql, queryParams, Collections.emptyList());
    }

    /**
     * Строит SQL-запрос DELETE для удаления одной сущности.
     *
     * @param entityName  Имя сущности в онтологии.
     * @param idFieldName Имя поля первичного ключа для WHERE-условия.
     * @return Объект QueryResult, содержащий SQL и пустую карту параметров (параметр ID будет добавлен в сервисе).
     */
    public QueryResult buildDelete(String entityName, String idFieldName) {
        OntologyDto.EntitySchema schema = ontologyService.getEntitySchema(entityName);
        String primaryTable = schema.getMeta().getPrimaryTable();

        OntologyDto.EntitySchema.FieldSchema idFieldSchema = findFieldInSchema(schema, idFieldName);
        if (idFieldSchema == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось найти схему для первичного ключа '" + idFieldName + "' в сущности '" + entityName + "'.");
        }

        String whereClause = String.format("WHERE %s = :id_param", idFieldSchema.getDb().getColumn());

        String sql = String.format("DELETE FROM %s %s",
                primaryTable,
                whereClause
        );

        return new QueryResult(sql, Collections.emptyMap(), Collections.emptyList());
    }

    /**
     * Строит SQL-запрос UPDATE для одной сущности.
     *
     * @param entityName     Имя сущности в онтологии.
     * @param fieldsToUpdate Карта полей для обновления { "fieldName": "newValue" }.
     * @param idFieldName    Имя поля первичного ключа для WHERE-условия.
     * @return Объект QueryResult, содержащий SQL и параметры.
     */
    public QueryResult buildUpdate(String entityName, Map<String, Object> fieldsToUpdate, String idFieldName) {
        OntologyDto.EntitySchema schema = ontologyService.getEntitySchema(entityName);
        String primaryTable = schema.getMeta().getPrimaryTable();

        List<String> setClauses = new ArrayList<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : fieldsToUpdate.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            OntologyDto.EntitySchema.FieldSchema fieldSchema = findFieldInSchema(schema, fieldName);
            if (fieldSchema == null) {
                log.warn("Попытка обновить несуществующее поле '{}' в сущности '{}'. Поле проигнорировано.", fieldName, entityName);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Попытка обновить несуществующее поле '" + fieldName + "' в сущности '" + entityName + "'.");
            }

            if (Boolean.TRUE.equals(fieldSchema.getDb().getIsPrimaryKey())) {
                log.warn("Попытка обновить первичный ключ '{}' в сущности '{}'. Поле проигнорировано.", fieldName, entityName);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Попытка обновить первичный ключ '" + fieldName + "' в сущности '" + entityName + "' не разрешена.");
            }

            if (!primaryTable.equals(fieldSchema.getDb().getTable())) {
                log.warn("Попытка обновить поле '{}' из связанной таблицы '{}' через основной эндпоинт сущности '{}'. Поле проигнорировано.", fieldName, fieldSchema.getDb().getTable(), entityName);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Попытка обновить поле '" + fieldName + "' из связанной таблицы '" + fieldSchema.getDb().getTable() + "' через основной эндпоинт сущности '" + entityName + "' не разрешена.");
            }

            String paramName = "set_" + fieldName;
            setClauses.add(String.format("%s = :%s", fieldSchema.getDb().getColumn(), paramName));

            Object typedValue = convertValueToType(String.valueOf(value), fieldSchema.getType());
            queryParams.put(paramName, typedValue);
        }

        if (setClauses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет валидных полей для обновления.");
        }

        OntologyDto.EntitySchema.FieldSchema idFieldSchema = findFieldInSchema(schema, idFieldName);
        String whereClause = String.format("WHERE %s = :id_param", idFieldSchema.getDb().getColumn());

        String sql = String.format("UPDATE %s SET %s %s",
                primaryTable,
                String.join(", ", setClauses),
                whereClause
        );

        return new QueryResult(sql, queryParams, Collections.emptyList());
    }

    private String buildSqlStringForSingle(QueryContext context) {
        if (context.getSelectFields().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не выбрано ни одного поля для SELECT.");
        }
        String selectClause = context.getSelectFields().stream()
                .map(fi -> String.format("%s.%s AS %s", fi.getTableAlias(), fi.getColumnName(), fi.getColumnAlias()))
                .collect(Collectors.joining(", "));

        String fromClause = String.format("FROM %s %s",
                context.getRootEntitySchema().getMeta().getPrimaryTable(),
                context.getRootTableAlias());

        String joinClauses = String.join("\n", context.getJoinClauses().values());

        String whereClause = "";
        if (!context.getWhereClauses().isEmpty()) {
            whereClause = "WHERE " + String.join(" AND ", context.getWhereClauses());
        }

        String limitClause = "LIMIT 1";

        return String.format("SELECT %s\n%s\n%s\n%s\n%s",
                selectClause, fromClause, joinClauses, whereClause, limitClause).trim();
    }

    private String buildSqlString(QueryContext context, Pageable pageable) {
        return buildSqlStringForList(context, pageable);
    }

    /**
     * Рекурсивно или итеративно разрешает путь к полю, добавляя необходимые JOIN'ы в контекст.
     *
     * @param fullPath Полный путь к полю (например, "orders.status").
     * @param context  Текущий контекст запроса.
     * @return Метаинформация о разрешенном поле.
     */
    private FieldInfo resolvePath(String fullPath, QueryContext context) {
        List<String> pathParts = new ArrayList<>(Arrays.asList(fullPath.split("\\.")));
        OntologyDto.EntitySchema currentSchema = context.getRootEntitySchema();
        String currentTableAlias = context.getRootTableAlias();
        String currentPathKey = "";

        for (int i = 0; i < pathParts.size(); i++) {
            String part = pathParts.get(i);
            boolean isLastPart = (i == pathParts.size() - 1);

            OntologyDto.EntitySchema.FieldSchema fieldSchema = findFieldInSchema(currentSchema, part);

            if (fieldSchema != null) {
                String relationName = fieldSchema.getDb() != null ? fieldSchema.getDb().getRelationName() : null;

                if (StringUtils.hasText(relationName)) {
                    OntologyDto.EntitySchema.RelationSchema relation = currentSchema.getRelations().get(relationName);
                    if (relation == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поле '" + part + "' ссылается на несуществующую связь '" + relationName + "'");
                    }
                    OntologyDto.EntitySchema targetSchema = ontologyService.getEntitySchema(relation.getTargetEntity());
                    String targetColumnName = fieldSchema.getDb().getColumn();

                    String targetFieldName = targetSchema.getFields().stream()
                            .filter(f -> f.getDb() != null && targetColumnName.equals(f.getDb().getColumn()))
                            .findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось найти поле для колонки '" + targetColumnName + "' в сущности '" + targetSchema.getMeta().getUserFriendlyName() + "'"))
                            .getName();

                    pathParts.set(i, relationName);
                    pathParts.add(i + 1, targetFieldName);

                    part = pathParts.get(i);
                    isLastPart = (i == pathParts.size() - 1);

                } else if (isLastPart) {
                    return createFieldInfo(fullPath, currentSchema.getMeta().getEntityNamePlural(), part, fieldSchema, currentTableAlias);
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поле '" + part + "' в пути '" + fullPath + "' не является связью и не может иметь вложенных элементов.");
                }
            }

            OntologyDto.EntitySchema.RelationSchema relationSchema = currentSchema.getRelations().get(part);
            if (relationSchema == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Путь '" + fullPath + "' не может быть разрешен. '" + part + "' не является ни полем, ни связью в сущности '" + currentSchema.getMeta().getUserFriendlyName() + "'.");
            }

            currentPathKey = currentPathKey.isEmpty() ? part : currentPathKey + "." + part;

            if (context.getResolvedPathAliases().containsKey(currentPathKey)) {
                currentTableAlias = context.getResolvedPathAliases().get(currentPathKey);
            } else {
                String newTableAlias = context.getNextTableAlias();
                String joinClause = String.format("LEFT JOIN %s %s ON %s.%s = %s.%s",
                        relationSchema.getTargetTable(),
                        newTableAlias,
                        currentTableAlias,
                        relationSchema.getSourceColumn(),
                        newTableAlias,
                        relationSchema.getTargetColumn()
                );

                if (StringUtils.hasText(relationSchema.getJoinCondition())) {
                    String condition = relationSchema.getJoinCondition().replace("targetAlias", newTableAlias);
                    joinClause += " AND " + condition;
                }

                context.getJoinClauses().put(currentPathKey, joinClause);
                context.getResolvedPathAliases().put(currentPathKey, newTableAlias);
                currentTableAlias = newTableAlias;
            }

            currentSchema = ontologyService.getEntitySchema(relationSchema.getTargetEntity());
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Путь '" + fullPath + "' не удалось разрешить до конечного поля. Путь указывает на сущность, а не на поле.");
    }

    private OntologyDto.EntitySchema.FieldSchema findFieldInSchema(OntologyDto.EntitySchema schema, String fieldName) {
        return schema.getFields().stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    private FieldInfo createFieldInfo(String fullPath, String entityName, String fieldName, OntologyDto.EntitySchema.FieldSchema fieldSchema, String tableAlias) {
        String columnAlias = fullPath.replace('.', '_');
        return new FieldInfo(entityName, fieldName, fullPath, tableAlias, fieldSchema.getDb().getColumn(), columnAlias);
    }

    /**
     * Добавляет условие WHERE в контекст.
     */
    private void processFilter(FieldInfo fieldInfo, String stringValue, QueryContext context) {
        String paramName = context.getNextParamName();
        String whereClause = String.format("%s.%s = :%s",
                fieldInfo.getTableAlias(),
                fieldInfo.getColumnName(),
                paramName);

        OntologyDto.EntitySchema.FieldSchema fieldSchema = findFieldByFullPath(fieldInfo.getFullPath(), context.getRootEntitySchema());
        Object typedValue = convertValueToType(stringValue, fieldSchema.getType());

        context.getWhereClauses().add(whereClause);
        context.getQueryParams().put(paramName, typedValue);
    }

    /**
     * Вспомогательный метод для поиска FieldSchema по полному пути.
     * Нужен для получения типа поля в processFilter.
     */
    private OntologyDto.EntitySchema.FieldSchema findFieldByFullPath(String fullPath, OntologyDto.EntitySchema rootSchema) {
        String[] parts = fullPath.split("\\.");
        OntologyDto.EntitySchema currentSchema = rootSchema;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isLastPart = i == parts.length - 1;

            OntologyDto.EntitySchema.FieldSchema field = findFieldInSchema(currentSchema, part);
            if (field == null) {
                OntologyDto.EntitySchema.RelationSchema relation = currentSchema.getRelations().get(part);
                if (relation == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный путь в findFieldByFullPath: " + part);
                currentSchema = ontologyService.getEntitySchema(relation.getTargetEntity());
                continue;
            }

            String relationName = field.getDb() != null ? field.getDb().getRelationName() : null;
            if (StringUtils.hasText(relationName)) {
                OntologyDto.EntitySchema.RelationSchema relation = currentSchema.getRelations().get(relationName);
                currentSchema = ontologyService.getEntitySchema(relation.getTargetEntity());
            } else if (isLastPart) {
                return field;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось найти схему для поля: " + fullPath);
    }

    /**
     * Преобразует строковое значение в типизированное на основе типа из онтологии.
     * @param value Строковое значение из фильтра.
     * @param ontologyType Тип поля из онтологии (e.g., "string", "boolean", "decimal", "uuid").
     * @return Объект нужного типа (String, Boolean, BigDecimal, UUID, etc.).
     */
    private Object convertValueToType(String value, String ontologyType) {
        if (value == null || "null".equalsIgnoreCase(value)) { // Добавим проверку на строку "null"
            return null;
        }
        try {
            switch (ontologyType.toLowerCase()) {
                case "boolean":
                    return Boolean.parseBoolean(value);
                case "decimal":
                case "numeric":
                    return new BigDecimal(value);
                case "integer":
                case "int":
                    return Integer.parseInt(value);
                case "long":
                    return Long.parseLong(value);
                case "uuid":
                    return UUID.fromString(value);
                case "date":
                    try {
                        return ZonedDateTime.parse(value).toLocalDate();
                    } catch (DateTimeParseException e1) {
                        try {
                            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (DateTimeParseException e2) {
                            throw e2;
                        }
                    }
                case "string":
                case "text":
                default:
                    return value;
            }
        } catch (Exception e) {
            log.warn("Не удалось преобразовать значение '{}' к типу '{}'. Будет использовано строковое представление. Ошибка: {}", value, ontologyType, e.getMessage());
            throw new IllegalArgumentException("Ошибка преобразования значения '" + value + "' к типу '" + ontologyType + "'", e);
        }
    }

    /**
     * Собирает финальную SQL-строку из контекста.
     */
    private String buildSqlStringForList(QueryContext context, Pageable pageable) {
        String selectClause;
        if (context.getSelectFields().isEmpty()) {
            context.getRootEntitySchema().getFields().forEach(fieldSchema -> {
                FieldInfo resolvedField = resolvePath(fieldSchema.getName(), context);
                context.getSelectFields().add(resolvedField);
            });
        }

        selectClause = context.getSelectFields().stream()
                .map(fi -> String.format("%s.%s AS %s", fi.getTableAlias(), fi.getColumnName(), fi.getColumnAlias()))
                .collect(Collectors.joining(", "));

        String fromClause = String.format("FROM %s %s",
                context.getRootEntitySchema().getMeta().getPrimaryTable(),
                context.getRootTableAlias());

        String joinClauses = String.join("\n", context.getJoinClauses().values());

        String whereClause = "";
        if (!context.getWhereClauses().isEmpty()) {
            whereClause = "WHERE " + String.join(" AND ", context.getWhereClauses());
        }

        String orderByClause = "";
        if (pageable.getSort().isSorted()) {
            orderByClause = "ORDER BY " + pageable.getSort().stream()
                    .map(order -> convertSortOrderToString(order, context))
                    .collect(Collectors.joining(", "));
        }

        String limitOffsetClause = String.format("LIMIT %d OFFSET %d",
                pageable.getPageSize(),
                pageable.getOffset());

        return String.format("SELECT %s\n%s\n%s\n%s\n%s\n%s",
                selectClause, fromClause, joinClauses, whereClause, orderByClause, limitOffsetClause);
    }

    /**
     * Конвертирует объект Sort.Order в строку для SQL.
     * Требует доработки для поддержки сортировки по связанным полям.
     */
    private String convertSortOrderToString(Sort.Order order, QueryContext context) {
        try {
            FieldInfo fieldInfo = resolvePath(order.getProperty(), context);
            return String.format("%s.%s %s", fieldInfo.getTableAlias(), fieldInfo.getColumnName(), order.getDirection());
        } catch (Exception e) {
            OntologyDto.EntitySchema.FieldSchema fieldSchema = findFieldInSchema(context.getRootEntitySchema(), order.getProperty());
            if (fieldSchema != null) {
                return String.format("%s.%s %s", context.getRootTableAlias(), fieldSchema.getDb().getColumn(), order.getDirection());
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Невозможно отсортировать по неизвестному полю: " + order.getProperty());
        }
    }

    /**
     * Строит SQL-запрос для поиска ID одной сущности с LIMIT 2 для быстрой проверки на уникальность.
     *
     * @param entityName Имя корневой сущности.
     * @param query      Объект с условиями фильтрации.
     * @return Объект QueryResult, содержащий SQL для поиска ID и накопленные параметры.
     */
    public QueryResult buildFindSingleId(String entityName, EntitiesSearchRequestDto.QueryDto query) {
        OntologyDto.EntitySchema rootSchema = ontologyService.getEntitySchema(entityName);
        QueryContext context = new QueryContext(rootSchema);

        if (query != null) {
            String whereContent = processQuery(query, context);
            if (StringUtils.hasText(whereContent)) {
                context.getWhereClauses().add(whereContent);
            }
        }

        String primaryKeyColumn = getPrimaryKeyColumn(rootSchema);
        String selectClause = String.format("SELECT %s.%s", context.getRootTableAlias(), primaryKeyColumn);

        String fromClause = String.format("FROM %s %s",
                rootSchema.getMeta().getPrimaryTable(),
                context.getRootTableAlias()
        );

        String joinClauses = String.join("\n", context.getJoinClauses().values());

        String whereClause = context.getWhereClauses().isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", context.getWhereClauses());

        String limitClause = "LIMIT 2";

        String sql = String.format("%s\n%s\n%s\n%s\n%s",
                selectClause, fromClause, joinClauses, whereClause, limitClause
        ).trim();

        return new QueryResult(sql, context.getQueryParams(), Collections.emptyList());
    }
}