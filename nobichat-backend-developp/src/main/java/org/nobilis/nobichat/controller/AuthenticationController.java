package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.SuccessResponseDto;
import org.nobilis.nobichat.dto.auth.AuthenticationResponseDto;
import org.nobilis.nobichat.dto.auth.LoginRequestDto;
import org.nobilis.nobichat.dto.auth.RegistrationRequestDto;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.service.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для регистрации/аутентификации пользователя")
@Validated
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Correct registration"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<AuthenticationResponseDto> register(@RequestBody RegistrationRequestDto registrationDto) {
        return ResponseEntity.ok(authenticationService.register(registrationDto));
    }

    @PostMapping("/login")
    @Operation(summary = "Аутентификация пользователя",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Correct authentication"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<AuthenticationResponseDto> login(@RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход из учетной записи",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Correct logout"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<SuccessResponseDto> logout() {
        authenticationService.logout();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new SuccessResponseDto("Токен удален"));
    }

    @PostMapping("/refreshToken")
    @Operation(summary = "Обновление токена",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Correct refresh"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "RefreshToken недействителен",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<AuthenticationResponseDto> refreshToken(@Parameter(hidden = true)
                                                                      @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authenticationService.refreshToken(authHeader));
    }
}
