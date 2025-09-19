package org.nobilis.nobichat.dto.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nobilis.nobichat.dto.chat.NavigationResponseDto;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SchemaTemplateResponseDto")
public class TemplateResponseDto {
    private JsonNode schema;
    private NavigationResponseDto.NavigationInfoDto navigationInfo;
}
