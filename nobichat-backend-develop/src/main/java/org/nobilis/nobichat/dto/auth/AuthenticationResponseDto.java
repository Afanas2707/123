package org.nobilis.nobichat.dto.auth;

import lombok.Data;

@Data
public class AuthenticationResponseDto {
    private final String accessToken;
    private final String refreshToken;
}
