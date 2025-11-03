package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.exception.ChatFlowException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScenarioValidatorService {

    private final OntologyService ontologyService;
    private static final Set<String> VALID_TEMPLATES = Set.of("form");

    public void validateCompleteness(ScenarioDefinition definition) {
        List<String> errors = new ArrayList<>();
        if (definition == null) {
            throw new ChatFlowException("Определение сценария полностью отсутствует.");
        }

        if (!StringUtils.hasText(definition.getName())) errors.add("Не указано название сценария.");
        if (!StringUtils.hasText(definition.getDescription())) errors.add("Не указано описание сценария.");

        if (CollectionUtils.isEmpty(definition.getSteps())) {
            errors.add("Сценарий должен содержать хотя бы один шаг.");
        } else {
            for (ScenarioDefinition.ScenarioStep step : definition.getSteps()) {
                validateStepCompleteness(step, errors, definition.getSteps().indexOf(step) + 1);
            }
        }
        if (!errors.isEmpty()) {
            throw new ChatFlowException(errors);
        }
    }

    public void validateCorrectness(ScenarioDefinition definition) {
        if (definition == null || CollectionUtils.isEmpty(definition.getSteps())) {
            return;
        }

        for (ScenarioDefinition.ScenarioStep step : definition.getSteps()) {
            validateStepCorrectness(step, definition.getSteps().indexOf(step) + 1);
        }
    }

    private void validateStepCompleteness(ScenarioDefinition.ScenarioStep step, List<String> errors, int index) {
        String stepIdentifier = getStepIdentifier(step, index);
        if (!StringUtils.hasText(step.getName())) errors.add(String.format("Шаг №%d: Не указано название.", index));
        if (!StringUtils.hasText(step.getDescription())) errors.add(stepIdentifier + ": Не указано описание.");
        if (!StringUtils.hasText(step.getTemplate())) errors.add(stepIdentifier + ": Не выбран шаблон.");
        if (!StringUtils.hasText(step.getEntity())) errors.add(stepIdentifier + ": Не указана сущность.");
        if (CollectionUtils.isEmpty(step.getEntityFields())) errors.add(stepIdentifier + ": Не выбраны поля для отображения.");
    }

    private void validateStepCorrectness(ScenarioDefinition.ScenarioStep step, int index) {
        String stepIdentifier = getStepIdentifier(step, index);

        if (StringUtils.hasText(step.getTemplate()) && !VALID_TEMPLATES.contains(step.getTemplate().toLowerCase())) {
            throw new ChatFlowException(stepIdentifier + String.format(": Указан несуществующий шаблон '%s'. Допустимые значения: %s.", step.getTemplate(), VALID_TEMPLATES));
        }

        if (StringUtils.hasText(step.getEntity())) {
            if (!ontologyService.entityExists(step.getEntity())) {
                throw new ChatFlowException(stepIdentifier + String.format(": Указана несуществующая сущность '%s'.", step.getEntity()));
            } else if (!CollectionUtils.isEmpty(step.getEntityFields())) {
                for (String fieldName : step.getEntityFields()) {
                    if (ontologyService.getFieldSchemaOptional(step.getEntity(), fieldName).isEmpty()) {
                        throw new ChatFlowException(stepIdentifier + String.format(": Указано несуществующее поле '%s' для сущности '%s'.", fieldName, step.getEntity()));
                    }
                }
            }
        }
    }

    private String getStepIdentifier(ScenarioDefinition.ScenarioStep step, int index) {
        int stepIndex = step.getStepIndex() != null ? step.getStepIndex() : index;
        return StringUtils.hasText(step.getName()) ? String.format("Шаг '%s' (№%d)", step.getName(), stepIndex) : String.format("Шаг №%d", stepIndex);
    }
}