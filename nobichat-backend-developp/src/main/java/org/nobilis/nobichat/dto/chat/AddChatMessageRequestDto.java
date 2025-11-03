package org.nobilis.nobichat.dto.chat;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.nobilis.nobichat.constants.ChatMode;

import java.util.UUID;

@Data
public class AddChatMessageRequestDto {

    @NotNull(message = "Session ID")
    @Schema(description = "Идентификатор сессии чата", requiredMode = Schema.RequiredMode.REQUIRED, example = "4897e1c1-fee2-4c6f-9569-d6c56ea3e36b")
    private UUID sessionId;

    @Schema(description = "Текст сообщения от пользователя", requiredMode = Schema.RequiredMode.REQUIRED, example = "Привет, чат!")
    private String userRequestText;

    @Schema(description = "Текст ответа AI", requiredMode = Schema.RequiredMode.REQUIRED, example = "Привет! Чем могу помочь?")
    private String aiResponseText;

    @NotNull(message = "Режим чата")
    @Schema(description = "Режим чата (STRICT или SOFT)", requiredMode = Schema.RequiredMode.REQUIRED, example = "STRICT")
    private ChatMode mode;

    private String frontendTemplateKey;
    private String frontendEntityName;
    private UUID frontendSourceId;
}
