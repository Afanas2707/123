package org.nobilis.nobichat.dto.template;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nobilis.nobichat.constants.ChatMode;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponseDto {
    private UUID id;
    private JsonNode schema;
    private ChatMode mode;
}
