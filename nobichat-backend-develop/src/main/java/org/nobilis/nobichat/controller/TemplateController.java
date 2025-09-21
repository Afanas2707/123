package org.nobilis.nobichat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.dto.SuccessResponseDto;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.dto.template.BulkReplaceRequestDto;
import org.nobilis.nobichat.dto.template.CreateTemplateRequestDto;
import org.nobilis.nobichat.dto.template.TemplateResponseDto;
import org.nobilis.nobichat.dto.template.ViewGenerationRequestDto;
import org.nobilis.nobichat.mapper.TemplateMapper;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.service.HistoryService;
import org.nobilis.nobichat.service.TemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/templates")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для управления шаблонами")
@Validated
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateMapper templateMapper;
    private final HistoryService historyService;

    @Deprecated
    @PostMapping
    @Operation(summary = "Создание нового шаблона",
            responses = {
                    @ApiResponse(responseCode = "201", description = "CREATED"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Конфликт (шаблон в STRICT режиме уже существует)",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<TemplateResponseDto> createTemplate(@Valid @RequestBody CreateTemplateRequestDto templateDto) {
        Template createdTemplate = templateService.createTemplate(templateDto);
        return new ResponseEntity<>(templateMapper.toDto(createdTemplate), HttpStatus.CREATED);
    }

    @Deprecated
    @DeleteMapping("/{id}")
    @Operation(summary = "Удаление шаблона",
            responses = {
                    @ApiResponse(responseCode = "204", description = "NO_CONTENT"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @Deprecated
    @GetMapping("/{id}")
    @Operation(summary = "Получение шаблона по id",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<TemplateResponseDto> getTemplateById(
            @PathVariable UUID id,
            @RequestParam(name = "entityId", required = false)
            @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) {
        Template template = templateService.getTemplateById(id);
        TemplateResponseDto dto = templateMapper.toDto(template);
        dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId));
        return ResponseEntity.ok(dto);
    }


    @Deprecated
    @GetMapping("/strict/latest")
    @Operation(summary = "Получение списка последних версий СТРОГИХ шаблонов",
            description = "Возвращает список последних версий для каждого view.id с mode=STRICT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<List<TemplateResponseDto>> getAllLatestStrictTemplates(
            @RequestParam(name = "entityId", required = false)
            @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) {
        List<Template> templates = templateService.getAllLatestStrictTemplates();
        List<TemplateResponseDto> dtoList = templateMapper.toDtoList(templates);
        List<TemplateResponseDto> modifiedDtoList = dtoList.stream()
                .peek(dto -> dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(modifiedDtoList);
    }

    @Deprecated
    @GetMapping("/strict/by-view/{viewId}")
    @Operation(summary = "Получение всех версий СТРОГОГО шаблона по view.id",
            description = "Возвращает список всех версий для view.id с mode=STRICT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Шаблоны не найдены",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<List<TemplateResponseDto>> getAllStrictTemplateVersionsByAppletId(
                                                                                             @PathVariable("viewId") String viewId,
                                                                                             @RequestParam(name = "entityId", required = false)
                                                                                             @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) { // Обновлено описание
        List<Template> templates = templateService.getAllStrictTemplateVersionsByAppletId(viewId);
        List<TemplateResponseDto> dtoList = templateMapper.toDtoList(templates);
        List<TemplateResponseDto> modifiedDtoList = dtoList.stream()
                .peek(dto -> dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(modifiedDtoList);
    }

    @Deprecated
    @GetMapping("/strict/by-view/{viewId}/versions/{viewVersion}")
    @Operation(summary = "Получение ОДНОГО СТРОГОГО шаблона по view.id и версии",
            description = "Возвращает один уникальный шаблон с mode=STRICT или 404.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<TemplateResponseDto> getStrictTemplateByAppletIdAndVersion(
                                                                                      @PathVariable("viewId") String viewId,
                                                                                      @PathVariable("viewVersion") String viewVersion,
                                                                                      @RequestParam(name = "entityId", required = false)
                                                                                      @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) { // Обновлено описание
        Template template = templateService.getStrictTemplateByAppletIdAndVersion(viewId, viewVersion);
        TemplateResponseDto dto = templateMapper.toDto(template);
        dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId));
        return ResponseEntity.ok(dto);
    }

    @Deprecated
    @GetMapping("/strict/by-view/{viewId}/latest")
    @Operation(summary = "Получение последней версии СТРОГОГО шаблона для view.id",
            description = "Возвращает один, самый последний шаблон с mode=STRICT для view.id или 404.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<TemplateResponseDto> getLatestStrictTemplateByAppletId(
                                                                                  @PathVariable("viewId") String viewId,
                                                                                  @RequestParam(name = "entityId", required = false)
                                                                                  @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) { // Обновлено описание
        Template template = templateService.getLatestStrictTemplateByAppletId(viewId);
        TemplateResponseDto dto = templateMapper.toDto(template);
        dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId));
        return ResponseEntity.ok(dto);
    }

    @Deprecated
    @GetMapping("/soft/latest")
    @Operation(summary = "Получение списка последних версий МЯГКИХ шаблонов",
            description = "Возвращает список последних версий для каждого view.id с mode=SOFT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<List<TemplateResponseDto>> getAllLatestSoftTemplates(
            @RequestParam(name = "entityId", required = false)
            @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) {
        List<Template> templates = templateService.getAllLatestSoftTemplates();
        List<TemplateResponseDto> dtoList = templateMapper.toDtoList(templates);
        List<TemplateResponseDto> modifiedDtoList = dtoList.stream()
                .peek(dto -> dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(modifiedDtoList);
    }

    @Deprecated
    @GetMapping("/soft/by-view/{viewId}")
    @Operation(summary = "Получение всех версий МЯГКОГО шаблона по view.id",
            description = "Возвращает список всех версий для view.id с mode=SOFT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<List<TemplateResponseDto>> getAllSoftTemplateVersionsByAppletId(
                                                                                           @PathVariable("viewId") String viewId,
                                                                                           @RequestParam(name = "entityId", required = false)
                                                                                           @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) { // Обновлено описание
        List<Template> templates = templateService.getAllSoftTemplateVersionsByAppletId(viewId);
        List<TemplateResponseDto> dtoList = templateMapper.toDtoList(templates);
        List<TemplateResponseDto> modifiedDtoList = dtoList.stream()
                .peek(dto -> dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(modifiedDtoList);
    }

    @Deprecated
    @GetMapping("/soft/by-view/{viewId}/versions/{viewVersion}")
    @Operation(summary = "Получение списка МЯГКИХ шаблонов по view.id и версии",
            description = "Возвращает список (возможно, из нескольких) шаблонов с mode=SOFT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<List<TemplateResponseDto>> getSoftTemplatesByAppletIdAndVersion(
                                                                                           @PathVariable("viewId") String viewId,
                                                                                           @PathVariable("viewVersion") String viewVersion,
                                                                                           @RequestParam(name = "entityId", required = false)
                                                                                           @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) { // Обновлено описание
        List<Template> templates = templateService.getSoftTemplatesByAppletIdAndVersion(viewId, viewVersion);
        List<TemplateResponseDto> dtoList = templateMapper.toDtoList(templates);
        List<TemplateResponseDto> modifiedDtoList = dtoList.stream()
                .peek(dto -> dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(modifiedDtoList);
    }

    @Deprecated
    @GetMapping("/soft/by-view/{viewId}/latest")
    @Operation(summary = "Получение списка последних версий МЯГКОГО шаблона для view.id",
            description = "Возвращает список (возможно, из нескольких) последних версий шаблонов с mode=SOFT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<List<TemplateResponseDto>> getLatestSoftTemplatesByAppletId(
                                                                                       @PathVariable("viewId") String viewId,
                                                                                       @RequestParam(name = "entityId", required = false)
                                                                                       @Parameter(description = "ID сущности для динамической настройки view.entity в схеме шаблона.") String entityId) { // Обновлено описание
        List<Template> templates = templateService.getLatestSoftTemplatesByAppletId(viewId);
        List<TemplateResponseDto> dtoList = templateMapper.toDtoList(templates);
        List<TemplateResponseDto> modifiedDtoList = dtoList.stream()
                .peek(dto -> dto.setSchema(modifySchemaForEntity(dto.getSchema(), entityId)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(modifiedDtoList);
    }

    private JsonNode modifySchemaForEntity(JsonNode originalSchema, String entityId) {
        if (originalSchema == null || entityId == null || entityId.isEmpty()) {
            return originalSchema;
        }

        ObjectNode modifiableSchema = originalSchema.deepCopy();

        if (modifiableSchema.has("view") && modifiableSchema.get("view").isObject()) {
            ObjectNode viewNode = (ObjectNode) modifiableSchema.get("view");
            viewNode.put("sourceId", entityId);
        }

        return modifiableSchema;
    }


    @PostMapping("/bulk-replace")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIGA_ADMIN', 'EDITOR')")
    @Operation(summary = "Массовая замена используемого шаблона в прошлых сообщениях чатов",
            description = """
                    Позволяет заменить один шаблон на другой для целой группы сообщений, отобранных по query-параметрам.
                    Критерии фильтрации применяются к ТЕКУЩЕМУ шаблону, привязанному к сообщению.
                    Хотя бы один фильтр должен быть указан. Можно комбинировать несколько фильтров.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Операция успешно выполнена",
                            content = @Content(schema = @Schema(implementation = SuccessResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Некорректный запрос (например, не указан фильтр)",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Новый шаблон с 'newTemplateId' не найден",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Доступ запрещен",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<SuccessResponseDto> bulkReplaceTemplateUsage(
            @Valid @RequestBody BulkReplaceRequestDto request,
            @Parameter(description = "Фильтр по ID старого шаблона, который нужно заменить.")
            @RequestParam(name = "filterByOldTemplateId", required = false) UUID filterByOldTemplateId,
            @Parameter(description = "Фильтр по режиму (STRICT/SOFT) старых шаблонов.")
            @RequestParam(name = "filterByMode", required = false) ChatMode filterByMode,
            @Parameter(description = "Фильтр по значению 'view.id' в schema старых шаблонов.")
            @RequestParam(name = "filterByViewId", required = false) String filterByViewId,
            @Parameter(description = "Фильтр по значению 'view.version' в schema старых шаблонов.")
            @RequestParam(name = "filterByViewVersion", required = false) String filterByViewVersion) {

        if (filterByOldTemplateId == null && filterByMode == null && filterByViewId == null && filterByViewVersion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо указать хотя бы один query-параметр для фильтрации: filterByOldTemplateId, filterByMode, filterByViewId или filterByViewVersion."); // Обновлено сообщение
        }

        Integer updatedCount = templateService.bulkReplaceTemplateUsage(
                request.getNewTemplateId(),
                filterByOldTemplateId,
                filterByMode,
                filterByViewId,
                filterByViewVersion
        );

        String message = String.format("Операция завершена. Обновлено шаблонов: %d", updatedCount);
        return ResponseEntity.ok(new SuccessResponseDto(message));
    }


    @PostMapping("/generate/{viewId}")
    @Operation(summary = "Динамическая генерация UI-шаблона (ListView/FormView) в строгом режиме",
            description = """
                    Генерирует JSON-схему пользовательского интерфейса (ListView или FormView)
                    на основе данных из тела запроса и онтологии.
                    - `viewId` должен быть в формате `entityName.viewType` (например, `customer.list.view` или `customer.edit.form`).
                    - Для `edit.form` в теле запроса требуется обязательное поле `entityId`.
                    Генерация происходит в строгом режиме: поля выбираются на основе флагов `isDefaultInList` или `isDefaultInCard` в онтологии.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<ChatResponseDto> getDynamicViewStrict(
            @Parameter(description = "Тело запроса с идентификатором представления и ID сущности.", required = true)
            @RequestBody @Valid ViewGenerationRequestDto requestDto) {

        ChatResponseDto responseDto = historyService.generateAndRecordView(requestDto);

        return ResponseEntity.ok(responseDto);
    }
}