package org.nobilis.nobichat.dto.supplier.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * "Грязный" DTO (Payload) для частичного обновления поставщика.
 * Используется только на уровне контроллера для десериализации входящего JSON.
 * Каждый изменяемый атрибут на любом уровне вложенности обернут в JsonNullable
 * для точного отслеживания переданных полей.
 */
@Data
public class SupplierUpdateDtoWrapped {
    private JsonNullable<@NotBlank(message = "Название поставщика не может быть пустым") String> name = JsonNullable.undefined();
    private JsonNullable<String> description = JsonNullable.undefined();
    private JsonNullable<String> supplierCode = JsonNullable.undefined();
    private JsonNullable<String> directorName = JsonNullable.undefined();
    private JsonNullable<Boolean> active = JsonNullable.undefined();
    private JsonNullable<@NotBlank(message = "ИНН не может быть пустым") String> inn = JsonNullable.undefined();
    private JsonNullable<String> contractNumber = JsonNullable.undefined();
    private JsonNullable<String> legalAddress = JsonNullable.undefined();
    private JsonNullable<String> kpp = JsonNullable.undefined();
    private JsonNullable<String> ogrn = JsonNullable.undefined();
    private JsonNullable<String> okpo = JsonNullable.undefined();
    private JsonNullable<String> bankName = JsonNullable.undefined();
    private JsonNullable<String> correspondentAccount = JsonNullable.undefined();

    private JsonNullable<@Valid List<ContactPayload>> contacts = JsonNullable.undefined();
    private JsonNullable<@Valid List<EventPayload>> events = JsonNullable.undefined();
    private JsonNullable<@Valid List<FilePayload>> files = JsonNullable.undefined();
    private JsonNullable<@Valid List<NomenclaturePayload>> nomenclatures = JsonNullable.undefined();
    private JsonNullable<@Valid List<PricePayload>> prices = JsonNullable.undefined();
    private JsonNullable<@Valid List<OrderPayload>> orders = JsonNullable.undefined();

    /**
     * Payload для обновления Контакта.
     * Поле 'id' используется для идентификации существующего контакта для обновления.
     * Если 'id' null или отсутствует, это может быть расценено как создание нового контакта.
     */
    @Data
    @Schema(name = "SupplierUpdateContactPayload")
    public static class ContactPayload {
        private UUID id;
        private JsonNullable<String> fullName = JsonNullable.undefined();
        private JsonNullable<String> position = JsonNullable.undefined();
        private JsonNullable<String> email = JsonNullable.undefined();
        private JsonNullable<String> phone = JsonNullable.undefined();
        private JsonNullable<String> notes = JsonNullable.undefined();
        private JsonNullable<Boolean> isPrimary = JsonNullable.undefined();
    }

    @Data
    @Schema(name = "SupplierUpdateEventPayload")
    public static class EventPayload {
        private UUID id;
        private JsonNullable<UUID> userId = JsonNullable.undefined();
        private JsonNullable<String> eventType = JsonNullable.undefined();
        private JsonNullable<String> title = JsonNullable.undefined();
        private JsonNullable<String> description = JsonNullable.undefined();
        private JsonNullable<LocalDateTime> eventDate = JsonNullable.undefined();
    }

    @Data
    @Schema(name = "SupplierUpdateFilePayload")
    public static class FilePayload {
        private UUID id;
        private JsonNullable<UUID> uploadedByUserId = JsonNullable.undefined();
        private JsonNullable<String> fileName = JsonNullable.undefined();
        private JsonNullable<String> description = JsonNullable.undefined();
    }

    @Data
    @Schema(name = "SupplierUpdateNomenclaturePayload")
    public static class NomenclaturePayload {
        private UUID id;
        private JsonNullable<String> articleNumber = JsonNullable.undefined();
        private JsonNullable<String> name = JsonNullable.undefined();
        private JsonNullable<String> description = JsonNullable.undefined();
        private JsonNullable<String> category = JsonNullable.undefined();
        private JsonNullable<String> unit = JsonNullable.undefined();
    }

    @Data
    @Schema(name = "SupplierUpdatePricePayload")
    public static class PricePayload {
        private UUID id;
        private JsonNullable<String> articleNumber = JsonNullable.undefined();
        private JsonNullable<String> name = JsonNullable.undefined();
        private JsonNullable<String> unit = JsonNullable.undefined();
        private JsonNullable<BigDecimal> price = JsonNullable.undefined();
    }

    @Data
    @Schema(name = "SupplierUpdateOrderPayload")
    public static class OrderPayload {
        private UUID id;
        private JsonNullable<String> orderNumber = JsonNullable.undefined();
        private JsonNullable<String> status = JsonNullable.undefined();
        private JsonNullable<BigDecimal> totalAmount = JsonNullable.undefined();
        private JsonNullable<LocalDateTime> orderDate = JsonNullable.undefined();
        private JsonNullable<@Valid List<OrderItemPayload>> items = JsonNullable.undefined();
    }

    @Data
    @Schema(name = "SupplierUpdateOrderItemPayload")
    public static class OrderItemPayload {
        private UUID id;
        private JsonNullable<String> articleNumber = JsonNullable.undefined();
        private JsonNullable<String> name = JsonNullable.undefined();
        private JsonNullable<String> unit = JsonNullable.undefined();
        private JsonNullable<BigDecimal> quantity = JsonNullable.undefined();
        private JsonNullable<BigDecimal> pricePerUnit = JsonNullable.undefined();
        private JsonNullable<BigDecimal> lineTotal = JsonNullable.undefined();
    }
}