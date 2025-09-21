package org.nobilis.nobichat.mapper.scenario;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.scenario.ScenarioTemplateDto;
import org.nobilis.nobichat.mapper.ui.UiComponentMapper;
import org.nobilis.nobichat.model.scenario.ScenarioTemplate;

@Mapper(componentModel = "spring",
        uses = {UiComponentMapper.class, ScenarioFieldBindingMapper.class, ScenarioStepMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScenarioTemplateMapper {

    @Mapping(target = "scenarioId", source = "scenario.id")
    @Mapping(target = "rootComponent", source = "rootComponent")
    @Mapping(target = "rootComponentId", source = "rootComponent.id")
    ScenarioTemplateDto toDto(ScenarioTemplate template);
}
