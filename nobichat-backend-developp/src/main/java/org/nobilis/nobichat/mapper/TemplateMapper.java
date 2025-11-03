package org.nobilis.nobichat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.template.TemplateResponseDto;
import org.nobilis.nobichat.model.Template;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TemplateMapper {

    TemplateResponseDto toDto(Template entity);

    List<TemplateResponseDto> toDtoList(List<Template> entities);
}
