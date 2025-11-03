package org.nobilis.nobichat.dto.organization;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequestDto {
    @NotBlank(message = "Поле organizationName обязательно для заполнения")
    private String organizationName;
}
