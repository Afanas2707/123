package org.nobilis.nobichat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UiSchemaDtos {

    /**
     * Корневой объект UI. Содержит информацию о сценарии и один View.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UiSchemaDto {
        private String scenarioName;
        private String scenarioDescription;
        private ViewDto view;
        private List<PanelDto> panels;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PanelDto {
        private String template;
        private List<StepperStepDto> stepper;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StepperStepDto {
        private String stepName;
        private Boolean active;
        private String stepDescription;
    }

    /**
     * Описание одного экрана (шага).
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ViewDto {
        private String stepTemplate;
        private String stepTitle;
        private String stepDescription;
        private Integer stepIndex;
        private List<FieldDto> entityFields;
        private String entity;
        private SourceDto source;
        private StateMarkdownDto stateMarkdown;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StateMarkdownDto {
        private String source;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SourceDto {
        private String method;
        private String endpoint;
        private SourceBodyDto body;
        private UUID sourceId;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SourceBodyDto {
        private List<String> fields;
    }

    /**
     * Описание одного поля на форме.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldDto {
        private String type;
        private String fieldName;
        private String label;
        private Boolean required;
        private int displaySequence;
    }
}