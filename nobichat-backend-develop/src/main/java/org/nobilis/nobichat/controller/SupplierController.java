package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.aspect.DtoFieldFilterService;
import org.nobilis.nobichat.aspect.PayloadToDtoConverter;
import org.nobilis.nobichat.dto.SuccessResponseDto;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.dto.supplier.SupplierCreateDto;
import org.nobilis.nobichat.dto.supplier.SupplierDetailDto;
import org.nobilis.nobichat.dto.supplier.SupplierListDto;
import org.nobilis.nobichat.dto.supplier.SupplierPaginatedResponseDto;
import org.nobilis.nobichat.dto.supplier.update.SupplierUpdateDto;
import org.nobilis.nobichat.dto.supplier.update.SupplierUpdateDtoWrapped;
import org.nobilis.nobichat.mapper.SupplierMapper;
import org.nobilis.nobichat.model.Supplier;
import org.nobilis.nobichat.service.SupplierFileService;
import org.nobilis.nobichat.service.SupplierService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "tokenAuth")
@RequestMapping("/api/entities/suppliers")
@Tag(name = "Контроллер для поставщиков")
@Validated
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierMapper supplierMapper;
    private final SupplierFileService supplierFileService;
    private final PayloadToDtoConverter payloadToDtoConverter;
    private final DtoFieldFilterService dtoFieldFilterService;

    @GetMapping
    @Operation(summary = "Таблица поставщиков",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<SupplierPaginatedResponseDto> getSuppliers(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size
    ) {
        Pageable pageable = PageRequest.of(page - 1, size);
        SupplierPaginatedResponseDto response = supplierService.findAllForCurrentUser(pageable, search);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Активировать или деактивировать поставщика",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<SupplierListDto> updateSupplierStatus(
            @Parameter(description = "ID поставщика") @PathVariable UUID id,
            @Parameter(name = "X-Chat-Session-Id", in = ParameterIn.HEADER, description = "ID сессии чата, в рамках которой был сгенерирован UI")
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @Parameter(description = "Новый статус активности (true или false)") @RequestParam Boolean active) {
        Supplier supplier = supplierService.updateStatus(id, active);
        SupplierListDto supplierListDto = supplierMapper.toSupplierListDto(supplier);
        return ResponseEntity.ok(supplierListDto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить поставщика",
            responses = {
                    @ApiResponse(responseCode = "204", description = "NO_CONTENT"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<Void> deleteSupplier(
            @Parameter(description = "ID поставщика") @PathVariable UUID id,
            @Parameter(name = "X-Chat-Session-Id", in = ParameterIn.HEADER, description = "ID сессии чата, в рамках которой был сгенерирован UI")
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId) {
        supplierService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получение карточки поставщика",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<SupplierDetailDto> getSupplier(
            @PathVariable UUID id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @GetMapping("/{id}/filtered")
    @Operation(
            summary = "Получение частичных данных поставщика",
            description = "Возвращает только те поля поставщика, которые были запрошены в параметре 'fields'. " +
                    "Поля перечисляются через запятую. Пример: fields=name,inn,active,contacts",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                                    value = """
                                    {
                                      "name": "ООО 'Инновационные Системы'",
                                      "active": false,
                                      "contacts": [
                                        {
                                          "id": "...",
                                          "fullName": "Алексей Смирнов",
                                          "isPrimary": true
                                        }
                                      ]
                                    }
                                    """
                            ))
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            }
    )
    public ResponseEntity<Map<String, Object>> getFilteredSupplier(
            @Parameter(description = "ID поставщика") @PathVariable UUID id,
            @Parameter(description = "Список полей через запятую") @RequestParam Set<String> fields
    ) {
        SupplierDetailDto fullDto = supplierService.getSupplierById(id);

        Map<String, Object> filteredDto = dtoFieldFilterService.filter(fullDto, fields);

        return ResponseEntity.ok(filteredDto);
    }

    @PostMapping
    @Operation(
            summary = "Создание карточки поставщика",
            description = "Создает нового поставщика со всеми связанными данными: контактами, номенклатурой, заказами и т.д.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SupplierCreateDto.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Пример создания IT-поставщика",
                                            summary = "Создание 'Инновационные Системы'",
                                            value = """
                                                    {
                                                      "name": "ООО 'Офис-Эксперт'",
                                                      "description": "Надежный поставщик канцелярских товаров и офисных принадлежностей.",
                                                      "supplierCode": "OE-1245",
                                                      "directorName": "Петров Петр Петрович",
                                                      "active": true,
                                                      "inn": "7712345678",
                                                      "contractNumber": "К-2024/OE-01",
                                                      "legalAddress": "109012, г. Москва, ул. Ильинка, д. 6/1, стр. 1",
                                                      "kpp": "771001001",
                                                      "ogrn": "1197746123456",
                                                      "okpo": "98765432",
                                                      "bankName": "ФИЛИАЛ 'ЦЕНТРАЛЬНЫЙ' ПАО 'ПРОМСВЯЗЬБАНК'",
                                                      "correspondentAccount": "30101810400000000555",
                                                      "contacts": [
                                                        {
                                                          "fullName": "Иванова Мария Ивановна",
                                                          "position": "Менеджер по продажам",
                                                          "email": "m.ivanova@office-expert.com",
                                                          "phone": "+7 (495) 555-10-20",
                                                          "notes": "Основной контакт для размещения заказов.",
                                                          "isPrimary": true
                                                        },
                                                        {
                                                          "fullName": "Сидоров Иван Петрович",
                                                          "position": "Логист",
                                                          "email": "i.sidorov@office-expert.com",
                                                          "phone": "+7 (495) 555-10-21",
                                                          "notes": "Вопросы по доставке и отгрузке.",
                                                          "isPrimary": false
                                                        }
                                                      ],
                                                      "events": [
                                                        {
                                                          "userId": "ab11d252-b3d4-42f6-8738-7c41142803e0",
                                                          "eventType": "Звонок",
                                                          "title": "Запрос коммерческого предложения",
                                                          "description": "Созвонились, запросили актуальный прайс-лист на бумагу и ручки.",
                                                          "eventDate": "2024-06-01T11:00:00"
                                                        }
                                                      ],
                                                      "files": [
                                                        {
                                                          "uploadedByUserId": "ab11d252-b3d4-42f6-8738-7c41142803e0",
                                                          "fileName": "Прайс-лист_Офис-Эксперт_июнь2024.xlsx",
                                                          "description": "Актуальные цены на весь ассортимент."
                                                        },
                                                        {
                                                          "uploadedByUserId": "ab11d252-b3d4-42f6-8738-7c41142803e0",
                                                          "fileName": "Реквизиты ООО Офис-Эксперт.docx",
                                                          "description": "Карточка предприятия."
                                                        }
                                                      ],
                                                      "nomenclatures": [
                                                        {
                                                          "articleNumber": "A4-P-80",
                                                          "name": "Бумага офисная 'Expert Print' A4",
                                                          "description": "Плотность 80 г/м², класс A+, 500 листов. Для цветной и ч/б печати.",
                                                          "category": "Бумажная продукция",
                                                          "unit": "пачка"
                                                        },
                                                        {
                                                          "articleNumber": "PEN-BL-GEL-07",
                                                          "name": "Ручка гелевая, синяя, 0.7 мм",
                                                          "description": "Ручка с гелевыми чернилами, мягкое письмо.",
                                                          "category": "Письменные принадлежности",
                                                          "unit": "шт"
                                                        },
                                                        {
                                                          "articleNumber": "ST-OFFICE",
                                                          "name": "Степлер офисный №24/6",
                                                          "description": "Степлер до 30 листов, металлический корпус.",
                                                          "category": "Офисные принадлежности",
                                                          "unit": "шт"
                                                        }
                                                      ],
                                                      "prices": [
                                                        {
                                                          "articleNumber": "A4-P-80",
                                                          "name": "Бумага офисная 'Expert Print' A4",
                                                          "unit": "пачка",
                                                          "price": 520.00
                                                        },
                                                        {
                                                          "articleNumber": "PEN-BL-GEL-07",
                                                          "name": "Ручка гелевая, синяя, 0.7 мм",
                                                          "unit": "шт",
                                                          "price": 35.50
                                                        },
                                                        {
                                                          "articleNumber": "ST-OFFICE",
                                                          "name": "Степлер офисный №24/6",
                                                          "unit": "шт",
                                                          "price": 450.00
                                                        }
                                                      ],
                                                      "orders": [
                                                        {
                                                          "orderNumber": "ЗАКАЗ-2024/06/25-1",
                                                          "status": "Доставлен",
                                                          "totalAmount": 14650.00,
                                                          "orderDate": "2024-06-25T14:20:00",
                                                          "items": [
                                                            {
                                                              "articleNumber": "A4-P-80",
                                                              "name": "Бумага офисная 'Expert Print' A4",
                                                              "unit": "пачка",
                                                              "quantity": 20,
                                                              "pricePerUnit": 520.00,
                                                              "lineTotal": 10400.00
                                                            },
                                                            {
                                                              "articleNumber": "PEN-BL-GEL-07",
                                                              "name": "Ручка гелевая, синяя, 0.7 мм",
                                                              "unit": "шт",
                                                              "quantity": 100,
                                                              "pricePerUnit": 35.50,
                                                              "lineTotal": 3550.00
                                                            },
                                                            {
                                                              "articleNumber": "ST-OFFICE",
                                                              "name": "Степлер офисный №24/6",
                                                              "unit": "шт",
                                                              "quantity": 2,
                                                              "pricePerUnit": 350.00,
                                                              "lineTotal": 700.00
                                                            }
                                                          ]
                                                        }
                                                      ]
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "CREATED"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<SupplierDetailDto> createSupplier(@Valid @RequestBody SupplierCreateDto dto,
                                                            @Parameter(name = "X-Chat-Session-Id", in = ParameterIn.HEADER, description = "ID сессии чата, в рамках которой был сгенерирован UI")
                                                            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId) {
        SupplierDetailDto createdSupplier = supplierService.createSupplier(dto);
        return new ResponseEntity<>(createdSupplier, HttpStatus.CREATED);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Обновление карточки поставщика",
            description = "Обновляет существующего поставщика",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Полный набор данных для обновления. ID самого поставщика передается в URL.",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SupplierUpdateDto.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Пример обновления IT-поставщика",
                                            summary = "Обновление 'Инновационные Системы'",
                                            value = """
                                                    {
                                                      "name": "ООО 'Инновационные Системы'",
                                                      "description": "Поставщик интегрированных IT-решений и облачных сервисов. Партнер года.",
                                                      "supplierCode": "IS-2024",
                                                      "directorName": "Михайлов Андрей Викторович",
                                                      "active": false,
                                                      "inn": "7701234567",
                                                      "contractNumber": "IS-2024/01-UPD",
                                                      "legalAddress": "101000, г. Москва, ул. Мясницкая, д. 20",
                                                      "kpp": "770301001",
                                                      "ogrn": "1187746012345",
                                                      "okpo": "87654321",
                                                      "bankName": "АО 'ЦИФРОВОЙ БИЗНЕС БАНК'",
                                                      "correspondentAccount": "30101810200000000789",
                                                      "contacts": [
                                                        {
                                                          "fullName": "Алексей Смирнов",
                                                          "position": "Руководитель отдела развития",
                                                          "email": "a.smirnov@innovate-sys.com",
                                                          "phone": "+7 (926) 555-12-34",
                                                          "notes": "Основной контакт по всем вопросам. Повышен.",
                                                          "isPrimary": true
                                                        }
                                                      ],
                                                      "events": [
                                                        {
                                                          "userId": "ab11d252-b3d4-42f6-8738-7c41142803e0",
                                                          "eventType": "Договор",
                                                          "title": "Подписание доп. соглашения",
                                                          "description": "Подписали ДС-1 к основному договору.",
                                                          "eventDate": "2024-07-01T12:00:00"
                                                        }
                                                      ],
                                                      "files": [
                                                        {
                                                          "uploadedByUserId": "ab11d252-b3d4-42f6-8738-7c41142803e0",
                                                          "fileName": "Договор IS-2024-01 (скан).pdf",
                                                          "description": "Подписанная версия основного договора."
                                                        },
                                                        {
                                                          "uploadedByUserId": "ab11d252-b3d4-42f6-8738-7c41142803e0",
                                                          "fileName": "Дополнительное соглашение ДС-1.pdf",
                                                          "description": "Изменение реквизитов и юридического адреса."
                                                        }
                                                      ],
                                                      "nomenclatures": [
                                                        {
                                                          "articleNumber": "DFS-LIC-1Y",
                                                          "name": "Лицензия на ПО 'DataFlow Suite' (1 год)",
                                                          "description": "Годовая подписка на платформу анализа данных. Включает базовую поддержку.",
                                                          "category": "Программное обеспечение",
                                                          "unit": "шт."
                                                        }
                                                      ],
                                                      "prices": [
                                                        {
                                                          "articleNumber": "DFS-LIC-1Y",
                                                          "name": "Лицензия на ПО 'DataFlow Suite' (1 год)",
                                                          "unit": "шт.",
                                                          "price": 18000.00
                                                        }
                                                      ],
                                                      "orders": [
                                                        {
                                                          "orderNumber": "ЗК-2024-SYS-02-PILOT",
                                                          "status": "В работе",
                                                          "totalAmount": 18000.00,
                                                          "orderDate": "2024-07-02T10:00:00",
                                                          "items": [
                                                            {
                                                              "articleNumber": "DFS-LIC-1Y",
                                                              "name": "Лицензия на ПО 'DataFlow Suite' (1 год)",
                                                              "unit": "шт.",
                                                              "quantity": 1,
                                                              "pricePerUnit": 18000.00,
                                                              "lineTotal": 18000.00
                                                            }
                                                          ]
                                                        }
                                                      ]
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<SupplierDetailDto> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierUpdateDtoWrapped payload,
            @Parameter(name = "X-Chat-Session-Id", in = ParameterIn.HEADER, description = "ID сессии чата, в рамках которой был сгенерирован UI")
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId) {

        SupplierUpdateDto dto = payloadToDtoConverter.convert(payload, SupplierUpdateDto.class);
        return ResponseEntity.ok(supplierService.updateSupplier(id, dto));
    }

    @PostMapping(value = "/{supplierId}/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузка файла для поставщика",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<SuccessResponseDto> uploadFile(
            @PathVariable UUID supplierId,
            @Parameter(name = "X-Chat-Session-Id", in = ParameterIn.HEADER, description = "ID сессии чата, в рамках которой был сгенерирован UI")
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @RequestPart(value = "file") MultipartFile file) {

        supplierFileService.uploadFileForSupplier(supplierId, file);
        return ResponseEntity.ok(new SuccessResponseDto("Файл успешно загружен"));
    }
}
