package org.nobilis.nobichat.mapper.scenario;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.scenario.ScenarioDto;
import org.nobilis.nobichat.model.scenario.Scenario;

@Mapper(componentModel = "spring",
        uses = {ScenarioTemplateMapper.class, ScenarioStepMapper.class, ScenarioTransitionMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScenarioMapper {

    @Mapping(target = "entityId", source = "entity.id")
    @Mapping(target = "entityName", source = "entity.name")
    ScenarioDto toDto(Scenario scenario);
}
