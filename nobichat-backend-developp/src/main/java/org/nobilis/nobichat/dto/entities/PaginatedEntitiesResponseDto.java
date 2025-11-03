package org.nobilis.nobichat.dto.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class PaginatedEntitiesResponseDto {

    @Schema(description = "Список сущностей на текущей странице")
    private List<Map<String, Object>> content;

    @Schema(description = "Общее количество найденных элементов", example = "42")
    private Long totalElements;

    @Schema(description = "Общее количество страниц", example = "5")
    private Integer totalPages;
}
