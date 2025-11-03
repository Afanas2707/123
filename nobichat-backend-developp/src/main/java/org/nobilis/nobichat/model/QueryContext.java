package org.nobilis.nobichat.model;

import lombok.Data;
import org.nobilis.nobichat.dto.ontology.OntologyDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class QueryContext {

    /**
     * Схема корневой сущности запроса, извлеченная из онтологии.
     */
    private final OntologyDto.EntitySchema rootEntitySchema;

    /**
     * Сгенерированный алиас для корневой таблицы. Обычно "t0".
     */
    private final String rootTableAlias;

    /**
     * Список полей, которые будут включены в SELECT-часть запроса.
     * Порядок важен для последующего маппинга результатов.
     */
    private final List<FieldInfo> selectFields = new ArrayList<>();

    /**
     * Список строковых представлений условий для WHERE-части запроса.
     * Например: "t0.inn = :param1", "t1.status = :param2"
     */
    private final List<String> whereClauses = new ArrayList<>();

    /**
     * Карта для хранения уже добавленных JOIN-ов, чтобы избежать дублирования.
     * Ключ: уникальный идентификатор join'а (например, путь к связи: "supplier.orders").
     * Значение: полная строка JOIN-условия (например, "LEFT JOIN public.supplier_orders t1 ON t0.id = t1.supplier_id").
     * Использование LinkedHashMap сохраняет порядок добавления JOIN'ов.
     */
    private final Map<String, String> joinClauses = new LinkedHashMap<>();

    /**
     * Карта параметров для WHERE-условий.
     * Ключ: имя параметра (например, "param1").
     * Значение: значение параметра (например, "12345").
     * Это необходимо для предотвращения SQL-инъекций.
     */
    private final Map<String, Object> queryParams = new LinkedHashMap<>();

    /**
     * Карта для отслеживания уже созданных алиасов таблиц для определенных путей связей.
     * Ключ: путь к связи (например, "supplier.orders").
     * Значение: сгенерированный алиас (например, "t1").
     */
    private final Map<String, String> resolvedPathAliases = new LinkedHashMap<>();

    /**
     * Счетчик для генерации уникальных алиасов таблиц (t1, t2, ...).
     */
    private int tableAliasCounter = 0;

    /**
     * Счетчик для генерации уникальных имен параметров (:param1, :param2, ...).
     */
    private int paramNameCounter = 0;

    public QueryContext(OntologyDto.EntitySchema rootEntitySchema) {
        this.rootEntitySchema = rootEntitySchema;
        this.rootTableAlias = "t" + tableAliasCounter++;
        this.resolvedPathAliases.put("", this.rootTableAlias);
    }

    /**
     * Генерирует и возвращает следующий уникальный алиас для таблицы.
     * @return Уникальный алиас, например, "t1", "t2".
     */
    public String getNextTableAlias() {
        return "t" + tableAliasCounter++;
    }

    /**
     * Генерирует и возвращает следующий уникальный алиас для параметра запроса.
     * @return Уникальный алиас, например, "param1", "param2".
     */
    public String getNextParamName() {
        return "param" + paramNameCounter++;
    }
}
