package org.nobilis.nobichat.dto.organization;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetOrganizationResponseDto {
    private UUID id;
    private String organizationName;
}