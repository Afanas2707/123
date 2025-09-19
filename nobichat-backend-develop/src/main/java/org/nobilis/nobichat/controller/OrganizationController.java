package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.dto.organization.CreateOrganizationRequestDto;
import org.nobilis.nobichat.dto.organization.GetOrganizationResponseDto;
import org.nobilis.nobichat.dto.organization.UpdateOrganizationRequestDto;
import org.nobilis.nobichat.mapper.OrganizationMapper;
import org.nobilis.nobichat.model.Organization;
import org.nobilis.nobichat.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organization")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для управления организациями")
@Validated
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationMapper organizationMapper;

    @PostMapping
    @Operation(summary = "Создание организации",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<GetOrganizationResponseDto> createOrganization(@Valid @RequestBody CreateOrganizationRequestDto request) {
        Organization createdOrganization = organizationService.createOrganization(request.getOrganizationName());
        return new ResponseEntity<>(organizationMapper.toDto(createdOrganization), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получение организации",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<GetOrganizationResponseDto> getOrganizationById(@PathVariable UUID id) {
        Organization organization = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(organizationMapper.toDto(organization));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменение организации",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<GetOrganizationResponseDto> updateOrganization(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationRequestDto request) {
        Organization updatedOrganization = organizationService.updateOrganization(id, request.getOrganizationName());
        return ResponseEntity.ok(organizationMapper.toDto(updatedOrganization));
    }
}
