package org.nobilis.nobichat.dto.supplier;

import lombok.Data;

import java.util.UUID;

@Data
public class SupplierListDto {
    private UUID id;
    private String name;
    private Boolean active;
    private String description;
    private String primaryContactName;
    private String logoUrl;
    private String contactAvatarUrl;
}