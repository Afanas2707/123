package org.nobilis.nobichat.dto.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class LLMMultipleRequestDto {
    @NotBlank
    @Schema(description = "Текст запроса к моделям", example = "Сколько будет 5 + 5?")
    private String prompt;

    @NotEmpty
    @Schema(description = "Список имен моделей для отправки запроса",
            example = "[\"google/gemma-2-9b-it:free\", \"meta-llama/llama-3.1-8b-instruct:free\"]")
    private List<String> models;
}
