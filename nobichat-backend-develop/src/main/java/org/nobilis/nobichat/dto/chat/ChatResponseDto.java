package org.nobilis.nobichat.dto.chat;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {
    private UUID sessionId;
    private JsonNode uiSchema;
    private String message;
    private NavigationResponseDto.NavigationInfoDto navigationInfo;
}
