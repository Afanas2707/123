package org.nobilis.nobichat.dto.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateEntityRequestDto {

    @Schema(description = "Карта полей для обновления. Ключ - имя поля, значение - новое значение.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "{\"description\": \"Новое описание\", \"active\": false}")
    @NotEmpty(message = "Поле 'fields' не может быть пустым.")
    private Map<String, Object> fields;
}
