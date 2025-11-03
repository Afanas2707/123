package org.nobilis.nobichat.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.entities.EntitiesSearchRequestDto;
import org.nobilis.nobichat.dto.entities.PaginatedEntitiesResponseDto;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.model.FieldInfo;
import org.nobilis.nobichat.model.QueryResult;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicEntityQueryService {

    private final DynamicQueryBuilder dynamicQueryBuilder;
    private final OntologyService ontologyService;

    @PersistenceContext
    private final EntityManager entityManager;


    @Transactional(readOnly = true)
    public Optional<UUID> findLastCreatedEntityId(String entityName) {
        log.debug("Поиск ID последней созданной сущности '{}'.", entityName);
        QueryResult queryResult = dynamicQueryBuilder.buildFindLastCreatedId(entityName);
        Query nativeQuery = entityManager.createNativeQuery(queryResult.getSql());

        try {
            Object result = nativeQuery.getSingleResult();
            if (result instanceof UUID) {
                return Optional.of((UUID) result);
            } else if (result != null) {
                return Optional.of(UUID.fromString(result.toString()));
            }
        } catch (NoResultException e) {
            log.warn("В таблице для сущности '{}' нет записей, не удалось найти последний ID.", entityName);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Ошибка при поиске последнего ID для сущности '{}'", entityName, e);
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public PaginatedEntitiesResponseDto findEntities(
            String entityName,
            List<String> fields,
            EntitiesSearchRequestDto.QueryDto query,
            Pageable pageable) {

        QueryResult dataQueryResult = dynamicQueryBuilder.build(entityName, fields, query, pageable);
        Query dataQuery = entityManager.createNativeQuery(dataQueryResult.getSql());
        dataQueryResult.getParams().forEach(dataQuery::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rawResults = dataQuery.getResultList();
        List<Map<String, Object>> content = mapRawResults(rawResults, dataQueryResult.getSelectedFields());

        QueryResult countQueryResult = dynamicQueryBuilder.buildCount(entityName, query);
        Query countQuery = entityManager.createNativeQuery(countQueryResult.getSql());
        countQueryResult.getParams().forEach(countQuery::setParameter);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        int totalPages = (pageable.getPageSize() == 0) ? 1 : (int) Math.ceil((double) totalElements / (double) pageable.getPageSize());

        return new PaginatedEntitiesResponseDto(content, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findEntityById(String entityName, UUID id, List<String> fields) {
        String primaryKeyFieldName = ontologyService.getEntitySchema(entityName).getFields().stream()
                .filter(f -> f.getDb() != null && Boolean.TRUE.equals(f.getDb().getIsPrimaryKey()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для сущности '" + entityName + "' не определен первичный ключ в онтологии."))
                .getName();

        Map<String, String> filters = Map.of(primaryKeyFieldName, id.toString());

        QueryResult queryResult = dynamicQueryBuilder.buildForSingle(entityName, fields, filters);

        Query query = entityManager.createNativeQuery(queryResult.getSql());
        for (Map.Entry<String, Object> param : queryResult.getParams().entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rawResults = query.getResultList();

        if (rawResults.isEmpty()) {
            return Optional.empty();
        } else {
            List<Map<String, Object>> mappedResults = mapRawResults(rawResults, queryResult.getSelectedFields());
            return Optional.of(mappedResults.get(0));
        }
    }

    @Transactional
    public Optional<Map<String, Object>> updateEntity(String entityName, UUID id, Map<String, Object> fieldsToUpdate) {
        if (findEntityById(entityName, id, Collections.singletonList("id")).isEmpty()) {
            return Optional.empty();
        }

        String primaryKeyFieldName = ontologyService.getEntitySchema(entityName).getFields().stream()
                .filter(f -> f.getDb() != null && Boolean.TRUE.equals(f.getDb().getIsPrimaryKey()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для сущности '" + entityName + "' не определен первичный ключ."))
                .getName();

        QueryResult queryResult = dynamicQueryBuilder.buildUpdate(entityName, fieldsToUpdate, primaryKeyFieldName);

        Query query = entityManager.createNativeQuery(queryResult.getSql());

        for (Map.Entry<String, Object> param : queryResult.getParams().entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }
        query.setParameter("id_param", id);

        int updatedRows = query.executeUpdate();

        log.info("Обновлено {} строк для сущности '{}' с ID {}", updatedRows, entityName, id);

        return findEntityById(entityName, id, null);
    }

    @Transactional
    public boolean deleteEntity(String entityName, UUID id) {
        String primaryKeyFieldName = ontologyService.getEntitySchema(entityName).getFields().stream()
                .filter(f -> f.getDb() != null && Boolean.TRUE.equals(f.getDb().getIsPrimaryKey()))
                .findFirst()
                .map(OntologyDto.EntitySchema.FieldSchema::getName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для сущности '" + entityName + "' не определен первичный ключ."));

        QueryResult queryResult = dynamicQueryBuilder.buildDelete(entityName, primaryKeyFieldName);

        Query query = entityManager.createNativeQuery(queryResult.getSql());
        query.setParameter("id_param", id);

        int deletedRows = query.executeUpdate();

        if (deletedRows > 0) {
            log.info("Удалена 1 строка для сущности '{}' с ID {}", entityName, id);
            return true;
        } else {
            log.warn("Попытка удалить несуществующую сущность '{}' с ID {}", entityName, id);
            return false;
        }
    }

    @Transactional
    public Map<String, Object> createEntity(String entityName, Map<String, Object> fieldsToCreate) {
        OntologyDto.EntitySchema schema = ontologyService.getEntitySchema(entityName);

        String pkFieldName = schema.getFields().stream()
                .filter(f -> f.getDb() != null && Boolean.TRUE.equals(f.getDb().getIsPrimaryKey()))
                .findFirst()
                .map(OntologyDto.EntitySchema.FieldSchema::getName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для сущности '" + entityName + "' не определен первичный ключ."));

        UUID newId = (UUID) fieldsToCreate.computeIfAbsent(pkFieldName, k -> UUID.randomUUID());

        QueryResult queryResult = dynamicQueryBuilder.buildInsert(entityName, fieldsToCreate);
        Query query = entityManager.createNativeQuery(queryResult.getSql());
        for (Map.Entry<String, Object> param : queryResult.getParams().entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }

        int insertedRows = query.executeUpdate();
        if (insertedRows == 0) {
            throw new RuntimeException("Не удалось создать сущность '" + entityName + "'.");
        }

        log.info("Создана 1 строка для сущности '{}' с ID {}", entityName, newId);

        return findEntityById(entityName, newId, null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось найти только что созданную сущность с ID: " + newId));
    }

    private List<Map<String, Object>> mapRawResults(List<?> rawResults, List<FieldInfo> selectedFields) {
        if (rawResults == null || rawResults.isEmpty()) {
            return Collections.emptyList();
        }

        return rawResults.stream()
                .map(rowObject -> {
                    Object[] row;
                    if (rowObject instanceof Object[]) {
                        row = (Object[]) rowObject;
                    } else {
                        row = new Object[]{rowObject};
                    }

                    Map<String, Object> mappedRow = new LinkedHashMap<>();
                    for (int i = 0; i < selectedFields.size(); i++) {
                        FieldInfo fieldInfo = selectedFields.get(i);
                        Object value = (row.length > i) ? row[i] : null;
                        mappedRow.put(fieldInfo.getFullPath(), value);
                    }
                    return mappedRow;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findSingleEntityId(String entityName, EntitiesSearchRequestDto.QueryDto query) {
        log.debug("Проверка на наличие единственной сущности '{}' по заданным критериям.", entityName);

        QueryResult queryResult = dynamicQueryBuilder.buildFindSingleId(entityName, query);

        Query nativeQuery = entityManager.createNativeQuery(queryResult.getSql());
        queryResult.getParams().forEach(nativeQuery::setParameter);

        @SuppressWarnings("unchecked")
        List<Object> results = nativeQuery.getResultList();

        if (results.size() == 1) {
            Object result = results.get(0);
            UUID foundId;
            if (result instanceof UUID) {
                foundId = (UUID) result;
            } else if (result != null) {
                try {
                    foundId = UUID.fromString(result.toString());
                } catch (IllegalArgumentException e) {
                    log.error("Не удалось преобразовать результат PK в UUID. Результат: {}", result, e);
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }

            log.info("Найдена ровно одна сущность '{}' с ID: {}", entityName, foundId);
            return Optional.of(foundId);
        } else {
            log.info("Найдено {} сущностей (не 1) для '{}'. Возвращаем пустой результат.", results.size(), entityName);
            return Optional.empty();
        }
    }

}
