package org.nobilis.nobichat.mapper.scenario;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.scenario.ScenarioTransitionDto;
import org.nobilis.nobichat.model.scenario.ScenarioTransition;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScenarioTransitionMapper {

    @Mapping(target = "scenarioId", source = "scenario.id")
    @Mapping(target = "fromStepId", source = "fromStep.id")
    @Mapping(target = "toStepId", source = "toStep.id")
    ScenarioTransitionDto toDto(ScenarioTransition transition);
}
