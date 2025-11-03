package org.nobilis.nobichat.dto.chat;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nobilis.nobichat.dto.UiSchemaDtos;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponseDto {
    private String message;
    private UUID sessionId;
    private UiSchemaDtos.UiSchemaDto uiSchema;

    private List<ErrorDto> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDto {
        private String message;
    }
}