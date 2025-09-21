package org.nobilis.nobichat.mapper.scenario;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.scenario.ScenarioStepDto;
import org.nobilis.nobichat.mapper.ui.UiComponentMapper;
import org.nobilis.nobichat.model.scenario.ScenarioStep;

@Mapper(componentModel = "spring", uses = UiComponentMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScenarioStepMapper {

    @Mapping(target = "scenarioId", source = "scenario.id")
    @Mapping(target = "templateId", source = "template.id")
    @Mapping(target = "entryComponent", source = "entryComponent")
    @Mapping(target = "entryComponentId", source = "entryComponent.id")
    ScenarioStepDto toDto(ScenarioStep step);
}
