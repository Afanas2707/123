package org.nobilis.nobichat.dto.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EntitiesSearchRequestDto {

    @Schema(description = "Список полей для включения в ответ. Должен содержать хотя бы одно поле.",
            example = "[\"name\", \"description\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Необходимо указать хотя бы одно поле для выборки в 'fields'.")
    private List<String> fields;

    @Schema(description = "Объект, описывающий условия поиска и фильтрации. Может быть null, если фильтры не нужны.")
    @Valid
    private QueryDto query;

    @Schema(description = "Номер страницы (начиная с 1).", defaultValue = "1", example = "1")
    private Integer page;
    @Schema(description = "Количество записей на странице.", defaultValue = "20", example = "20")
    private Integer perPage;
    @Schema(description = "Поле, по которому будет производиться сортировка.", example = "name")
    private String sortBy;
    @Schema(description = "Направление сортировки.", allowableValues = {"ASC", "DESC"}, example = "ASC")
    private String sortOrder;

    @Data
    public static class QueryDto {

        @Schema(description = "Логический оператор для объединения условий.", defaultValue = "AND",
                allowableValues = {"AND", "OR"}, example = "AND")
        private String operator = "AND";

        @Schema(description = "Список условий фильтрации.")
        @Valid
        private List<ConditionDto> conditions;

        @Schema(description = "Список вложенных групп условий для создания сложных запросов.")
        @Valid
        private List<QueryDto> groups;

        @Data
        public static class ConditionDto {
            @Schema(description = "Имя поля для условия.", requiredMode = Schema.RequiredMode.REQUIRED, example = "name")
            @NotBlank(message = "Поле 'field' в условии не может быть пустым.")
            private String field;

            @Schema(description = "Оператор сравнения.", requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"equals", "contains", "not_equals", "greater_than", "less_than"}, example = "contains")
            @NotBlank(message = "Поле 'operator' в условии не может быть пустым.")
            private String operator;

            @Schema(description = "Значение для сравнения.", requiredMode = Schema.RequiredMode.REQUIRED, example = "ООО Ромашка")
            @NotNull(message = "Поле 'value' в условии не может быть null.")
            private Object value;
        }
    }
}
