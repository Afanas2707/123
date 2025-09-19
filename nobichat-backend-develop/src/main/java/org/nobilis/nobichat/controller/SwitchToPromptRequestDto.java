package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SwitchToPromptRequestDto {

    @NotNull(message = "promptId не может быть null.")
    @Schema(description = "ID сообщения (промпта), на которое необходимо переключить UI-состояние.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID promptId;
}
