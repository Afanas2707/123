package org.nobilis.nobichat.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class QueryResult {
    /**
     * Сгенерированная SQL-строка запроса.
     */
    private final String sql;

    /**
     * Карта параметров для WHERE-условий, для безопасной передачи в PreparedStatement.
     */
    private final Map<String, Object> params;

    /**
     * Список полей, которые были включены в SELECT.
     * Необходим для корректного маппинга результатов в сервисе.
     */
    private final List<FieldInfo> selectedFields;
}
