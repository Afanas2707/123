package org.nobilis.nobichat.dto.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LLMPromptRequestDto {
    @NotBlank
    @Schema(description = "Текст запроса к моделям", example = "Сколько будет 5 + 5?")
    public String prompt;
}
