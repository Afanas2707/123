package org.nobilis.nobichat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldInfo {
    /**
     * Техническое имя сущности, к которой относится поле (из онтологии).
     * Например: "supplier", "supplierOrder"
     */
    private String entityName;

    /**
     * Техническое имя поля (из онтологии).
     * Например: "name", "orderNumber"
     */
    private String fieldName;

    /**
     * Полный путь к полю от корневой сущности запроса.
     * Используется для формирования уникального имени в ответе.
     * Например: "name" для поля корневой сущности или "orders.orderNumber" для поля связанной сущности.
     */
    private String fullPath;

    /**
     * Сгенерированный алиас таблицы, в которой находится это поле.
     * Например: "t0" для корневой таблицы, "t1" для первой присоединенной таблицы и т.д.
     */
    private String tableAlias;

    /**
     * Имя колонки в таблице базы данных, соответствующее этому полю.
     * Например: "name", "order_number"
     */
    private String columnName;

    /**
     * Уникальный алиас для колонки, который будет использоваться в SELECT-части SQL-запроса.
     * Это необходимо, чтобы избежать конфликтов имен колонок из разных таблиц.
     * Генерируется из полного пути (fullPath) для обеспечения уникальности.
     * Например: "supplier_name", "supplier_orders_orderNumber"
     */
    private String columnAlias;
}
