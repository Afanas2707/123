package org.nobilis.nobichat.dto.scenario;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class ScenarioDto {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private UUID entityId;
    private String entityName;
    private List<ScenarioTemplateDto> templates = new ArrayList<>();
    private List<ScenarioStepDto> steps = new ArrayList<>();
    private List<ScenarioTransitionDto> transitions = new ArrayList<>();
}
