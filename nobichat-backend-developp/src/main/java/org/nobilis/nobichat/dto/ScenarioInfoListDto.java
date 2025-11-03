package org.nobilis.nobichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioInfoListDto {

    @Schema(description = "Список всех доступных для запуска сценариев")
    private List<ScenarioInfo> scenarios;

    /**
     * DTO, содержащий базовую информацию о сценарии.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioInfo {
        @Schema(description = "Уникальный идентификатор сценария")
        private UUID id;

        @Schema(description = "Название сценария")
        private String name;
    }
}
