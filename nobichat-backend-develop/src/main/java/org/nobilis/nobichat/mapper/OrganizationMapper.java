package org.nobilis.nobichat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.nobilis.nobichat.dto.organization.GetOrganizationResponseDto;
import org.nobilis.nobichat.model.Organization;

import java.util.List;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrganizationMapper {

    @Mapping(source = "name", target = "organizationName")
    GetOrganizationResponseDto toDto(Organization organization);

    List<GetOrganizationResponseDto> toDtoList(List<Organization> organizations);
}
