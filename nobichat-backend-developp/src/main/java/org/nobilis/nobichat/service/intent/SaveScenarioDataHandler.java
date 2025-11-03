package org.nobilis.nobichat.service.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Scenario;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ScenarioRepository;
import org.nobilis.nobichat.service.DynamicEntityQueryService;
import org.nobilis.nobichat.service.ScenarioHelperService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SaveScenarioDataHandler implements IntentHandler {

    private final DynamicEntityQueryService dynamicEntityQueryService;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioHelperService scenarioHelperService;
    private final ObjectMapper objectMapper;

    @Override
    public String getIntentType() {
        return "SAVE_SCENARIO_DATA";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        ChatRequest.ChatRequestContextDto context = request.getContext();

        if (context == null || CollectionUtils.isEmpty(context.getEntities())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет данных для сохранения. Контекст пуст.");
        }

        UUID lastProcessedId = null;

        try {
            Map<String, Object> scenarioContextMap = objectMapper.readValue(session.getScenarioContextJson(), new TypeReference<>() {});

            for (ChatRequest.EntityContextDto entityContext : context.getEntities()) {
                String entityName = entityContext.getEntity();
                UUID sourceId = entityContext.getSourceId();

                Map<String, Object> fieldsMap = new HashMap<>();
                if (entityContext.getFields() != null) {
                    for (ChatRequest.FieldContextDto field : entityContext.getFields()) {
                        fieldsMap.put(field.getFieldName(), field.getValue());
                    }
                }

                Map<String, Object> savedEntity;
                if (sourceId != null) {
                    savedEntity = dynamicEntityQueryService.updateEntity(entityName, sourceId, fieldsMap)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сущность " + entityName + " с ID " + sourceId + " для обновления не найдена."));
                } else {
                    savedEntity = dynamicEntityQueryService.createEntity(entityName, fieldsMap);
                }

                Object savedId = savedEntity.get("id");
                if (savedId != null) {
                    lastProcessedId = UUID.fromString(savedId.toString());
                    scenarioContextMap.put(entityName + "Id", lastProcessedId.toString());
                }
            }

            session.setScenarioContextJson(objectMapper.writeValueAsString(scenarioContextMap));

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка обработки JSON контекста", e);
        }

        Scenario scenario = scenarioRepository.findById(session.getActiveScenarioId()).orElseThrow();
        ScenarioDefinition.ScenarioStep currentStep = scenario.getDefinition().getSteps().stream()
                .filter(s -> s.getName().equals(session.getCurrentStepName())).findFirst().orElseThrow();

        ChatResponseDto response = scenarioHelperService.createResponseForStep(session, scenario.getDefinition(), currentStep, lastProcessedId, false);

        String originalMessage = response.getMessage();
        response.setMessage(originalMessage + "\n\nДанные успешно сохранены.");

        return response;
    }
}