package org.nobilis.nobichat.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioDefinition {
    private String name;
    private String description;
    private List<ScenarioStep> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioStep {
        private String name;
        private String description;
        private Integer stepIndex;
        private String template;
        private String entity;

        private List<String> entityFields;
        private List<TransitionCondition> transitions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransitionCondition {
        private String condition;
        private String targetStepName;
        private boolean isLLMCheck;
        private boolean isDefault;
    }
}