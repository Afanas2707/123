package org.nobilis.nobichat.dto.template;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTemplateRequestDto {
    @NotNull(message = "Схема не может быть пустой")
    private JsonNode schema;
}