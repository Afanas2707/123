package org.nobilis.nobichat.dto.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponseDto {
    private String modelName;
    private String content;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    @Schema(description = "Время ответа модели в миллисекундах", type = "string", example = "1250ms")
    private String responseTime;
}