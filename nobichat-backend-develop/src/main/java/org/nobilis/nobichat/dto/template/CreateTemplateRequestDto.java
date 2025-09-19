package org.nobilis.nobichat.dto.template;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nobilis.nobichat.constants.ChatMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequestDto {
    @NotNull(message = "Схема не может быть пустой")
    private JsonNode schema;
    @NotNull(message = "Мод не может быть пустой")
    private ChatMode mode;
}
