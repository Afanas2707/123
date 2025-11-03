package org.nobilis.nobichat.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.nobilis.nobichat.constants.Role;

import java.util.UUID;

@Data
public class RegistrationRequestDto {
    @NotBlank
    private String username;
    @NotBlank
    private String email;
    @NotBlank
    private String password;
    @NotNull
    private Role role;
    @NotBlank
    private UUID organizationId;
}
