package org.nobilis.nobichat.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ChatRequest {

    @NotBlank
    @Schema(
            description = "Текстовое сообщение или команда от пользователя.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Создай новый сценарий"
    )
    private String message;

    @Schema(
            description = "Идентификатор текущей сессии чата. Если null, будет создана новая сессия.",
            example = "a1b2c3d4-e5f6-a7b8-i9j0-k1l2m3n4o5p6",
            nullable = true
    )
    private UUID sessionId;

    @Schema(
            description = "Контекст UI, содержащий данные с формы. Отправляется вместе с командой 'Сохранить'.",
            nullable = true
    )
    private ChatRequestContextDto context;

    @Data
    @Schema(name = "ChatRequestContext")
    public static class ChatRequestContextDto {
        @Schema(description = "Заголовок шага, с которого отправляются данные.", example = "Оформление заявки")
        private String stepTitle;

        @Schema(description = "Порядковый номер шага.", example = "2")
        private Integer stepIndex;

        @Schema(description = "Список сущностей с данными, которые нужно сохранить.")
        private List<EntityContextDto> entities;
    }

    @Data
    @Schema(name = "EntityContext")
    public static class EntityContextDto {
        @Schema(description = "Техническое имя сущности.", example = "customerEvent")
        private String entity;

        @Schema(
                description = "ID существующей записи, если данные нужно обновить (UPDATE). Если null - будет создана новая запись (CREATE).",
                example = "b1c2d3e4-f5a6-b7c8-d9e0-f1a2b3c4d5e6",
                nullable = true
        )
        private UUID sourceId;

        @Schema(description = "Список полей с их значениями.")
        private List<FieldContextDto> fields;
    }

    @Data
    @Schema(name = "FieldContext")
    public static class FieldContextDto {
        @Schema(description = "Техническое имя поля.", example = "title")
        private String fieldName;

        @Schema(description = "Значение поля, введенное пользователем.", example = "Заявка на возврат")
        private Object value;
    }
}