package org.nobilis.nobichat.dto.scenario;

import lombok.Data;
import org.nobilis.nobichat.dto.ui.UiComponentDto;

import java.util.UUID;

@Data
public class ScenarioFieldBindingDto {

    private UUID id;
    private UUID templateId;
    private UUID fieldId;
    private String fieldName;
    private String fieldUserFriendlyName;
    private String fieldType;
    private UiComponentDto component;
    private UUID componentId;
    private String bindingType;
    private Boolean required;
    private Integer displayOrder;
}
