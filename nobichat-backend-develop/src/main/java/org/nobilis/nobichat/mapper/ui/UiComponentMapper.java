package org.nobilis.nobichat.mapper.ui;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.ui.UiComponentDto;
import org.nobilis.nobichat.model.ui.UiComponent;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UiComponentMapper {

    UiComponentDto toDto(UiComponent component);
}
