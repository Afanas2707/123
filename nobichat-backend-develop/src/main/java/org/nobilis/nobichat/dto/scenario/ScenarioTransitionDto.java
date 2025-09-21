package org.nobilis.nobichat.dto.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

@Data
public class ScenarioTransitionDto {

    private UUID id;
    private UUID scenarioId;
    private UUID fromStepId;
    private UUID toStepId;
    private String conditionExpression;
    private JsonNode conditionConfig;
}
