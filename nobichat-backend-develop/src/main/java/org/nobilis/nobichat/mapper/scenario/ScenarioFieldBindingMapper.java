package org.nobilis.nobichat.mapper.scenario;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.scenario.ScenarioFieldBindingDto;
import org.nobilis.nobichat.mapper.ui.UiComponentMapper;
import org.nobilis.nobichat.model.scenario.ScenarioFieldBinding;

@Mapper(componentModel = "spring", uses = UiComponentMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScenarioFieldBindingMapper {

    @Mapping(target = "templateId", source = "template.id")
    @Mapping(target = "fieldId", source = "field.id")
    @Mapping(target = "fieldName", source = "field.name")
    @Mapping(target = "fieldUserFriendlyName", source = "field.userFriendlyName")
    @Mapping(target = "fieldType", source = "field.type")
    @Mapping(target = "component", source = "component")
    @Mapping(target = "componentId", source = "component.id")
    ScenarioFieldBindingDto toDto(ScenarioFieldBinding binding);
}
