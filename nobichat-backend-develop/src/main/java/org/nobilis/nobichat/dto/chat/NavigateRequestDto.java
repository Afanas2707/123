package org.nobilis.nobichat.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NavigateRequestDto {

    @NotBlank(message = "Направление не может быть пустым.")
    @Schema(description = "Направление навигации", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"BACK", "FORWARD"}, example = "BACK")
    private String direction;
}
