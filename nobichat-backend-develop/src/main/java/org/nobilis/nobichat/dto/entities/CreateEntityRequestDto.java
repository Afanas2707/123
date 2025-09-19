package org.nobilis.nobichat.dto.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class CreateEntityRequestDto {

    @Schema(description = "Карта полей для создания сущности. Ключ - имя поля, значение - значение.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "{\"name\": \"Новый поставщик\", \"inn\": \"1231231231\", \"active\": true}")
    @NotEmpty(message = "Поле 'fields' не может быть пустым.")
    private Map<String, Object> fields;
}