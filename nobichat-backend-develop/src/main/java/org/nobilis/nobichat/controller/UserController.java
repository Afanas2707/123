package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.constants.Role;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.dto.user.UserResponseDto;
import org.nobilis.nobichat.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для работы с пользователем")
@Validated
@RequiredArgsConstructor
public class UserController {

    private static final Set<Role> SOFT_MODE_ALLOWED_ROLES = Set.of(
            Role.ADMIN,
            Role.GIGA_ADMIN,
            Role.EDITOR
    );

    @GetMapping("/me")
    @Operation(
            summary = "Получение информации о текущем пользователе",
            description = "Возвращает данные пользователя, который аутентифицирован в системе (на основе JWT токена).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<UserResponseDto> getCurrentUserInfo() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Boolean canUseSoftMode = SOFT_MODE_ALLOWED_ROLES.contains(user.getRole());

        UserResponseDto response = UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .avatarUrl(String.format("/images/%s/%s", EntityType.user, user.getId()))
                .canUseSoftMode(canUseSoftMode)
                .build();

        return ResponseEntity.ok(response);
    }
}
