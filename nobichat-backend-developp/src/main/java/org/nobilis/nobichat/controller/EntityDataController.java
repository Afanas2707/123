package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.aspect.ProtectByUISchema;
import org.nobilis.nobichat.dto.entities.CreateEntityRequestDto;
import org.nobilis.nobichat.dto.entities.EntitiesSearchRequestDto;
import org.nobilis.nobichat.dto.entities.GetSingleEntityRequestDto;
import org.nobilis.nobichat.dto.entities.PaginatedEntitiesResponseDto;
import org.nobilis.nobichat.dto.entities.UpdateEntityRequestDto;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.service.DynamicEntityQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/entities")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Универсальный контроллер для всех сущностей, описанных в онтологии приложения", description = "API для динамического получения данных сущностей на основе онтологии")
@Validated
@RequiredArgsConstructor
public class EntityDataController {

    private final DynamicEntityQueryService dynamicEntityQueryService;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Получает список сущностей с возможностью фильтрации, выбора полей, сортировки и пагинации.
     *
     * @param entityName Имя сущности в онтологии (например, "supplier", "supplierOrder").
     */
    @PostMapping("/{entityName}/search")
    @Operation(
            summary = "Получить список сущностей по сложным критериям",
            description = "Возвращает пагинированный список сущностей на основе гибкого набора условий. " +
                    "Позволяет комбинировать фильтры с логическими операторами AND/OR.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Тело запроса для поиска, фильтрации и пагинации.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = EntitiesSearchRequestDto.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Простой поиск (AND)",
                                            summary = "Найти активных поставщиков, в названии которых есть 'ООО'",
                                            value = """
                                                    {
                                                      "fields": ["name", "inn", "active"],
                                                      "query": {
                                                        "operator": "AND",
                                                        "conditions": [
                                                          {
                                                            "field": "name",
                                                            "operator": "contains",
                                                            "value": "ООО"
                                                          },
                                                          {
                                                            "field": "active",
                                                            "operator": "equals",
                                                            "value": true
                                                          }
                                                        ]
                                                      },
                                                      "page": 1,
                                                      "perPage": 20,
                                                      "sortBy": "name",
                                                      "sortOrder": "ASC"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Сложный поиск (OR)",
                                            summary = "Найти поставщиков, у которых ИНН или КПП содержат '77'",
                                            value = """
                                                    {
                                                      "fields": ["name", "inn", "kpp"],
                                                      "query": {
                                                        "operator": "OR",
                                                        "conditions": [
                                                          {
                                                            "field": "inn",
                                                            "operator": "contains",
                                                            "value": "77"
                                                          },
                                                          {
                                                            "field": "kpp",
                                                            "operator": "contains",
                                                            "value": "77"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Поиск по дочерней сущности",
                                            summary = "Найти все файлы для конкретного поставщика",
                                            value = """
                                                    {
                                                      "fields": ["fileName", "description", "supplier.name"],
                                                      "query": {
                                                        "conditions": [
                                                          {
                                                            "field": "supplier.id",
                                                            "operator": "equals",
                                                            "value": "40c2c286-1d04-40ac-a882-ec99ecd1ab71"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                    """,
                                            description = "Этот запрос нужно отправлять на эндпоинт /api/entities/supplierFile/search"
                                    ),
                                    @ExampleObject(
                                            name = "Запрос без фильтров",
                                            summary = "Получить все записи с пагинацией и сортировкой",
                                            value = """
                                                    {
                                                      "fields": ["name", "inn"],
                                                      "query": null,
                                                      "page": 1,
                                                      "perPage": 50,
                                                      "sortBy": "name",
                                                      "sortOrder": "DESC"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Запрос дочерних сущностей (файлов)",
                                            summary = "Найти файлы поставщика, где имя файла или описание содержат ключевые слова",
                                            description = "Этот пример демонстрирует фильтрацию дочерней сущности ('supplierFile') по ID родительской ('supplier.id'), а также использование вложенной группы условий с оператором OR. Запрос отправляется на эндпоинт дочерней сущности: POST /api/entities/supplierFile/search",
                                            value = """
                                                    {
                                                      "fields": ["fileName", "description", "supplier.name"],
                                                      "query": {
                                                        "operator": "AND",
                                                        "conditions": [
                                                          {
                                                            "field": "supplier.id",
                                                            "operator": "equals",
                                                            "value": "2430d082-5485-4e13-bf5b-be62e8b5e13e"
                                                          }
                                                        ],
                                                        "groups": [
                                                          {
                                                            "operator": "OR",
                                                            "conditions": [
                                                              {
                                                                "field": "fileName",
                                                                "operator": "contains",
                                                                "value": "договор"
                                                              },
                                                              {
                                                                "field": "description",
                                                                "operator": "contains",
                                                                "value": "акт"
                                                              }
                                                            ]
                                                          }
                                                        ]
                                                      },
                                                      "page": 1,
                                                      "perPage": 10
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad Request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            }
    )
    @ProtectByUISchema(operationType = "READ_LIST")
    public ResponseEntity<PaginatedEntitiesResponseDto> searchEntities(
            @PathVariable String entityName,
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @Valid @RequestBody EntitiesSearchRequestDto request) {

        Pageable pageable = createPageableFromRequest(request);

        PaginatedEntitiesResponseDto result = dynamicEntityQueryService.findEntities(
                entityName,
                request.getFields(),
                request.getQuery(),
                pageable
        );
        return ResponseEntity.ok(result);
    }

    private Pageable createPageableFromRequest(EntitiesSearchRequestDto request) {
        int page = request.getPage() != null ? request.getPage() : DEFAULT_PAGE;
        int size = request.getPerPage() != null ? request.getPerPage() : DEFAULT_PAGE_SIZE;
        int zeroBasedPage = Math.max(0, page - 1);
        Sort sort;

        if (StringUtils.hasText(request.getSortBy())) {
            String property = request.getSortBy();
            Sort.Direction direction = Sort.Direction.fromString(
                    StringUtils.hasText(request.getSortOrder()) ? request.getSortOrder() : "ASC"
            );
            sort = Sort.by(direction, property);
        } else {
            sort = Sort.by(Sort.Direction.ASC, "id");
        }

        return PageRequest.of(zeroBasedPage, size, sort);
    }

    /**
     * Получает одну сущность по ее уникальному идентификатору (ID) с выбором полей из тела запроса.
     *
     * @param entityName Имя сущности в онтологии.
     * @param id         Уникальный идентификатор (UUID) сущности.
     * @param request    Тело запроса, содержащее список полей для выборки.
     * @return Объект, представляющий сущность, или 404 Not Found, если сущность не найдена.
     */
    @PostMapping("/{entityName}/{id}")
    @Operation(summary = "Получить сущность по ID",
            description = "Возвращает одну сущность по ее уникальному идентификатору. Поля для выборки можно указать в теле запроса.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    @ProtectByUISchema(operationType = "READ_BY_ID")
    public ResponseEntity<Map<String, Object>> getEntityById(
            @Parameter(description = "Техническое имя сущности из онтологии", required = true, example = "supplier")
            @PathVariable String entityName,

            @Parameter(description = "Уникальный идентификатор (UUID) сущности", required = true)
            @PathVariable UUID id,
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @RequestBody(required = false) GetSingleEntityRequestDto request) {

        GetSingleEntityRequestDto actualRequest = (request != null) ? request : new GetSingleEntityRequestDto();

        Optional<Map<String, Object>> result = dynamicEntityQueryService.findEntityById(
                entityName,
                id,
                actualRequest.getFields()
        );

        return result
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Частично обновляет сущность по ее ID, используя DTO в теле запроса.
     *
     * @param entityName Имя сущности в онтологии.
     * @param id         Уникальный идентификатор (UUID) сущности.
     * @param request    DTO с картой полей для обновления.
     * @return Обновленный объект сущности или 404 Not Found.
     */
    @PatchMapping("/{entityName}/{id}")
    @Operation(summary = "Частичное обновление сущности по ID",
            description = "Обновляет только те поля, которые переданы в поле 'fields' тела запроса. " +
                    "Ключи в карте должны соответствовать именам полей в онтологии.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    @ProtectByUISchema(operationType = "UPDATE", requiredAction = "action:update")
    public ResponseEntity<Map<String, Object>> updateEntity(
            @Parameter(description = "Техническое имя сущности из онтологии", required = true, example = "supplier")
            @PathVariable String entityName,

            @Parameter(description = "Уникальный идентификатор (UUID) сущности", required = true)
            @PathVariable UUID id,
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @Valid @RequestBody UpdateEntityRequestDto request) {

        Optional<Map<String, Object>> updatedEntity = dynamicEntityQueryService.updateEntity(
                entityName,
                id,
                request.getFields()
        );

        return updatedEntity
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создает новую сущность.
     *
     * @param entityName Имя сущности в онтологии.
     * @param request    DTO с данными для создания.
     * @return Созданный объект сущности со статусом 201 Created.
     */
    @PostMapping("/{entityName}")
    @Operation(summary = "Создание новой сущности",
            description = "Создает новую запись для указанной сущности. " +
                    "Ключи в теле запроса должны соответствовать именам полей в онтологии.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "CREATED"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    @ProtectByUISchema(operationType = "CREATE", requiredAction = "action:create")
    public ResponseEntity<Map<String, Object>> createEntity(
            @Parameter(description = "Техническое имя сущности из онтологии", required = true, example = "supplier")
            @PathVariable String entityName,
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @Valid @RequestBody CreateEntityRequestDto request) {

        Map<String, Object> createdEntity = dynamicEntityQueryService.createEntity(entityName, request.getFields());

        return ResponseEntity.status(HttpStatus.CREATED).body(createdEntity);
    }

    /**
     * Удаляет сущность по ее уникальному идентификатору (ID).
     *
     * @param entityName Имя сущности в онтологии.
     * @param id         Уникальный идентификатор (UUID) сущности.
     * @return 204 No Content в случае успеха или 404 Not Found, если сущность не найдена.
     */
    @DeleteMapping("/{entityName}/{id}")
    @Operation(summary = "Удаление сущности по ID",
            description = "Удаляет одну запись указанной сущности по ее уникальному идентификатору.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "NO_CONTENT"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    @ProtectByUISchema(operationType = "DELETE", requiredAction = "action:delete")
    public ResponseEntity<Void> deleteEntity(
            @Parameter(description = "Техническое имя сущности из онтологии", required = true, example = "supplier")
            @PathVariable String entityName,
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @Parameter(description = "Уникальный идентификатор (UUID) сущности", required = true)
            @PathVariable UUID id) {

        boolean deleted = dynamicEntityQueryService.deleteEntity(entityName, id);

        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}