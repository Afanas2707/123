package org.nobilis.nobichat.dto.ui;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

@Data
public class UiComponentDto {

    private UUID id;
    private String name;
    private String componentType;
    private String label;
    private String description;
    private Integer displayOrder;
    private JsonNode config;
}
