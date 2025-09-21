package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.constants.OntologyVersion;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.service.OntologyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ontology")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер Онтологии", description = "API для получения и управления метаданными системы")
@Validated
@RequiredArgsConstructor
@Slf4j
public class OntologyController {

    private final OntologyService ontologyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIGA_ADMIN', 'EDITOR')")
    @Operation(summary = "Получение онтологии",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<OntologyDto> getCurrentOntology() {
        OntologyDto ontology = ontologyService.getCurrentOntologySchema();
        return ResponseEntity.ok(ontology);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIGA_ADMIN', 'EDITOR')")
    @Operation(summary = "Обновление онтологии",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<OntologyDto> updateOntology(@RequestBody OntologyDto newOntology) {
        OntologyDto ontologyDto = ontologyService.updateOntology(newOntology);
        return ResponseEntity.ok(ontologyDto);
    }

    @GetMapping("/entities/{entityName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIGA_ADMIN', 'EDITOR')")
    @Operation(summary = "Получение детальной информации о сущности",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<OntologyDto.EntitySchema> getEntityByName(@PathVariable String entityName) {
        OntologyDto.EntitySchema entitySchema =
                ontologyService.getCurrentOntologySchema().getEntities().get(entityName);
        return ResponseEntity.ok(entitySchema);
    }

    @PostMapping("/update")
    @Operation(summary = "Обновить онтологию из файла",
            description = """
                    Загружает и устанавливает в качестве текущей одну из предопределенных версий онтологии.
                    Это действие полностью заменяет текущую схему онтологии в базе данных.
                    - `simplified`: Упрощенная версия онтологии.
                    - `advanced`: Расширенная версия онтологии.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<OntologyDto> updateOntologyVersion(
            @Parameter(description = "Версия онтологии для загрузки. Допустимые значения: `simplified`, `advanced`.",
                    required = true,
                    schema = @Schema(implementation = OntologyVersion.class))
            @RequestParam("version") @Valid OntologyVersion version) {
        log.info("Получен запрос на обновление онтологии до версии: {}", version);
        OntologyDto updatedOntology = ontologyService.updateOntologyFromFile(version);
        return ResponseEntity.ok(updatedOntology);
    }
}
