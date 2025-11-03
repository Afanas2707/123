package org.nobilis.nobichat.dto.chat.softMode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nobilis.nobichat.dto.entities.EntitiesSearchRequestDto;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentAndQueryResponse {
    private String intent;
    private String entity;
    private EntitiesSearchRequestDto.QueryDto query;
    private String sourceUrl;
    private String inn;
    private Integer itemIndex;
    private UUID scenarioId;
    private String scenarioDraftText;
    private Map<String, Object> scenarioInputData;
    private String scenarioName;
    private String direction;
}