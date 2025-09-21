package org.nobilis.nobichat.dto.scenario;

import lombok.Data;
import org.nobilis.nobichat.dto.ui.UiComponentDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class ScenarioTemplateDto {

    private UUID id;
    private UUID scenarioId;
    private String name;
    private String templateType;
    private String description;
    private boolean defaultTemplate;
    private UUID rootComponentId;
    private UiComponentDto rootComponent;
    private List<ScenarioFieldBindingDto> fieldBindings = new ArrayList<>();
    private List<ScenarioStepDto> steps = new ArrayList<>();
}
