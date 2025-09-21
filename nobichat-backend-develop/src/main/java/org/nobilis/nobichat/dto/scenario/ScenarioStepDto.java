package org.nobilis.nobichat.dto.scenario;

import lombok.Data;
import org.nobilis.nobichat.dto.ui.UiComponentDto;

import java.util.UUID;

@Data
public class ScenarioStepDto {

    private UUID id;
    private UUID scenarioId;
    private UUID templateId;
    private String name;
    private String label;
    private String description;
    private Integer displayOrder;
    private UUID entryComponentId;
    private UiComponentDto entryComponent;
}
