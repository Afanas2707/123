package org.nobilis.nobichat.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class ChatRequest {
    @NotBlank
    @Schema(description = "Текст сообщения от пользователя",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Покажи мне список поставщиков",
            nullable = false)
    private String message;
    @Schema(description = "Идентификатор сессии чата. Если null или отсутствует, будет создана новая сессия. " +
            "Если указан, сообщение будет отправлен промпт в уже существующую сессию",
            example = "null",
            nullable = true)
    private UUID sessionId;
    @Schema(description = "Флаг для управления кэшированием. Если true (по умолчанию) или не указан, система будет использовать кэш. " +
            "Если false, кэш будет проигнорирован, и UI-схема будет сгенерирована заново.",
            example = "false",
            defaultValue = "false",
            nullable = true)
    private Boolean useCache;
}