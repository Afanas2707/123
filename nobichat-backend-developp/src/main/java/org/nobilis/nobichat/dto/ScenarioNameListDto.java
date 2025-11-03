package org.nobilis.nobichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioNameListDto {

    @Schema(description = "Список названий всех доступных для запуска сценариев")
    private List<String> scenarioNames;
}

