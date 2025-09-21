package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.model.Image;
import org.nobilis.nobichat.service.ImageStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/images")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для получения/загрузки изображений")
@Validated
@RequiredArgsConstructor
public class ImageController {

    private final ImageStorageService imageStorageService;

    @PutMapping(value = "/{entityType}/{entityId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузка изображения для сущности на выбор(аватарки для пользователя или поставщика)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<byte[]> uploadImage(
            @Parameter(description = "Тип сущности (USER, SUPPLIER)",
                    schema = @Schema(implementation = EntityType.class))
            @PathVariable
            EntityType entityType,
            @Parameter(
                    description = "Уникальный идентификатор сущности (пользователя или поставщика)",
                    example = "ab11d252-b3d4-42f6-8738-7c41142803e0"
            )
            @PathVariable UUID entityId,
            @Parameter(description = "Файл изображения")
            @RequestPart(value = "file") MultipartFile file) {
        Image image = imageStorageService.saveImage(entityId, entityType, file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getImageData());
    }

    @GetMapping("/{entityType}/{entityId}")
    @Operation(summary = "Получение изображения для сущности на выбор(аватарки для пользователя или поставщика)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<byte[]> getImageOrDefault(
            @Parameter(description = "Тип сущности (USER, SUPPLIER)",
                    schema = @Schema(implementation = EntityType.class))
            @PathVariable EntityType entityType,
            @Parameter(
                    description = "Уникальный идентификатор сущности (пользователя или поставщика)",
                    example = "ab11d252-b3d4-42f6-8738-7c41142803e0"
            )
            @PathVariable UUID entityId) {
        Image image = imageStorageService.getImageOrDefault(entityId, entityType);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getImageData());
    }
}