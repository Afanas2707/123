package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScenarioFormatterService {

    private final OntologyService ontologyService;

    public String formatScenarioAsMarkdown(ScenarioDefinition definition) {
        if (definition == null) {
            return "Сценарий еще не был начат.";
        }

        StringBuilder md = new StringBuilder();

        md.append("# Название: ").append(formatOptional(definition.getName(), "(не задано)")).append("\n\n");
        md.append("## Описание\n");
        md.append(formatOptional(definition.getDescription(), "(не задано)")).append("\n\n");
        md.append("## Шаги сценария\n");

        if (CollectionUtils.isEmpty(definition.getSteps())) {
            md.append("*(Шаги пока не добавлены)*\n");
        } else {
            for (ScenarioDefinition.ScenarioStep step : definition.getSteps()) {
                int stepIndex = step.getStepIndex() != null ? step.getStepIndex() : (definition.getSteps().indexOf(step) + 1);

                md.append("### Шаг ").append(stepIndex).append("\n");
                md.append("Название: ").append(formatOptional(step.getName(), "(не задано)")).append("\n");
                md.append("Описание: ").append(formatOptional(step.getDescription(), "(не задано)")).append("\n");
                md.append("Шаблон: ").append(formatOptional(step.getTemplate(), "(не выбран)")).append("\n");
                md.append("Сущности:\n");

                if (StringUtils.hasText(step.getEntity())) {
                    try {
                        String entityFriendlyName = ontologyService.getEntityMetaData(step.getEntity()).getUserFriendlyName();
                        md.append("- ").append(entityFriendlyName).append("\n");

                        if (!CollectionUtils.isEmpty(step.getEntityFields())) {
                            for (String fieldName : step.getEntityFields()) {
                                String fieldFriendlyName = ontologyService.getFieldSchemaOptional(step.getEntity(), fieldName)
                                        .map(OntologyDto.EntitySchema.FieldSchema::getUserFriendlyName)
                                        .orElse(fieldName);
                                md.append("    - ").append(fieldFriendlyName).append("\n");
                            }
                        } else {
                            md.append("    *(Поля не выбраны)*\n");
                        }
                    } catch (Exception e) {
                        md.append("- ").append(step.getEntity()).append(" (ошибка: сущность не найдена в онтологии)\n");
                    }
                } else {
                    md.append("- (Сущность не выбрана)\n");
                }

                md.append("\n");
                md.append("Условия перехода:\n");
                md.append("- Нет условий\n\n");
            }
        }
        return md.toString();
    }

    private String formatOptional(String text, String placeholder) {
        return StringUtils.hasText(text) ? text : placeholder;
    }
}