package org.nobilis.nobichat.dto.supplier;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class SupplierCreateDto {

    private String name;
    private String description;
    private String supplierCode;
    private String directorName;
    private Boolean active;
    private String inn;
    private String contractNumber;
    private String legalAddress;
    private String kpp;
    private String ogrn;
    private String okpo;
    private String bankName;
    private String correspondentAccount;

    @Valid
    private List<Contact> contacts = new ArrayList<>();
    @Valid
    private List<Event> events = new ArrayList<>();
    @Valid
    private List<File> files = new ArrayList<>();
    @Valid
    private List<Nomenclature> nomenclatures = new ArrayList<>();
    @Valid
    private List<Price> prices = new ArrayList<>();
    @Valid
    private List<Order> orders = new ArrayList<>();

    @Data
    @Schema(name = "SupplierCreateContact")
    public static class Contact {
        private String fullName;
        private String position;
        private String email;
        private String phone;
        private String notes;
        private Boolean isPrimary;
    }

    @Data
    @Schema(name = "SupplierCreateEvent")
    public static class Event {
        private UUID userId;
        private String eventType;
        private String title;
        private String description;
        private LocalDateTime eventDate;
    }

    @Data
    @Schema(name = "SupplierCreateFile")
    public static class File {
        private UUID uploadedByUserId;
        private String fileName;
        private String description;
    }

    @Data
    @Schema(name = "SupplierCreateNomenclature")
    public static class Nomenclature {
        private String articleNumber;
        private String name;
        private String description;
        private String category;
        private String unit;
    }

    @Data
    @Schema(name = "SupplierCreatePrice")
    public static class Price {
        private String articleNumber;
        private String name;
        private String unit;
        private BigDecimal price;
    }

    @Data
    @Schema(name = "SupplierCreateOrder")
    public static class Order {
        private String orderNumber;
        private String status;
        private BigDecimal totalAmount;
        private LocalDateTime orderDate;

        @Valid
        private List<OrderItem> items = new ArrayList<>();
    }

    @Data
    @Schema(name = "SupplierCreateOrderItem")
    public static class OrderItem {
        private String articleNumber;
        private String name;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal pricePerUnit;
        private BigDecimal lineTotal;
    }
}