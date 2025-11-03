package org.nobilis.nobichat.dto.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class GetSingleEntityRequestDto {

    @Schema(description = "Список полей для включения в ответ. Если не указан, вернутся все поля корневой сущности.",
            example = "[\"name\", \"inn\", \"kpp\", \"directorName\"]")
    private List<String> fields;
}
