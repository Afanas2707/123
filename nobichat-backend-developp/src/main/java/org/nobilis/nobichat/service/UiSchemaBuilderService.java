package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.UiSchemaDtos;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UiSchemaBuilderService {

    private final OntologyService ontologyService;

    public UiSchemaDtos.UiSchemaDto buildFromStep(ScenarioDefinition scenario, ScenarioDefinition.ScenarioStep step, UUID sourceId, boolean isPreview) {
        UiSchemaDtos.SourceDto source = null;
        if (!isPreview) {
            source = buildSource(step, sourceId);
        }

        UiSchemaDtos.ViewDto viewDto = UiSchemaDtos.ViewDto.builder()
                .stepTemplate(mapTemplateToPath(step.getTemplate(), isPreview))
                .stepTitle(step.getName())
                .stepDescription(step.getDescription())
                .stepIndex(step.getStepIndex())
                .entity(step.getEntity())
                .entityFields(buildFields(step))
                .source(source)
                .build();

        return UiSchemaDtos.UiSchemaDto.builder()
                .scenarioName(scenario.getName())
                .scenarioDescription(scenario.getDescription())
                .view(viewDto)
                .panels(buildPanels(scenario, step))
                .build();
    }

    private List<UiSchemaDtos.PanelDto> buildPanels(ScenarioDefinition scenario, ScenarioDefinition.ScenarioStep currentStep) {
        List<UiSchemaDtos.StepperStepDto> stepperSteps = scenario.getSteps().stream()
                .sorted(Comparator.comparingInt(ScenarioDefinition.ScenarioStep::getStepIndex))
                .map(step -> UiSchemaDtos.StepperStepDto.builder()
                        .stepName(step.getName())
                        .stepDescription(step.getDescription())
                        .active(step.getName().equals(currentStep.getName()))
                        .build())
                .collect(Collectors.toList());

        UiSchemaDtos.PanelDto stepperPanel = UiSchemaDtos.PanelDto.builder()
                .template("panels/stepper/info-stepper")
                .stepper(stepperSteps)
                .build();

        return Collections.singletonList(stepperPanel);
    }

    private UiSchemaDtos.SourceDto buildSource(ScenarioDefinition.ScenarioStep step, UUID sourceId) {
        OntologyDto.EntitySchema schema = ontologyService.getEntitySchema(step.getEntity());
        String pkFieldName = schema.getFields().stream()
                .filter(f -> f.getDb() != null && Boolean.TRUE.equals(f.getDb().getIsPrimaryKey()))
                .map(OntologyDto.EntitySchema.FieldSchema::getName)
                .findFirst()
                .orElse("id");

        List<String> fieldsToRequest = new ArrayList<>(step.getEntityFields());
        if (!fieldsToRequest.contains(pkFieldName)) {
            fieldsToRequest.add(pkFieldName);
        }

        return UiSchemaDtos.SourceDto.builder()
                .method("POST")
                .endpoint(String.format("/entities/%s/{sourceId}", step.getEntity()))
                .sourceId(sourceId)
                .body(UiSchemaDtos.SourceBodyDto.builder()
                        .fields(fieldsToRequest)
                        .build())
                .build();
    }

    private List<UiSchemaDtos.FieldDto> buildFields(ScenarioDefinition.ScenarioStep step) {
        List<OntologyDto.EntitySchema.FieldSchema> allEntityFields = ontologyService.getFieldsForEntity(step.getEntity());

        return allEntityFields.stream()
                .filter(ontologyField -> step.getEntityFields().contains(ontologyField.getName()))
                .map(ontologyField -> UiSchemaDtos.FieldDto.builder()
                        .type(mapComponentType(ontologyField))
                        .fieldName(ontologyField.getName())
                        .label(ontologyField.getUi() != null && ontologyField.getUi().getFormView() != null && StringUtils.hasText(ontologyField.getUi().getFormView().getLabel())
                                ? ontologyField.getUi().getFormView().getLabel()
                                : ontologyField.getUserFriendlyName())
                        .required(ontologyField.getUi() != null && ontologyField.getUi().getFormView() != null && ontologyField.getUi().getFormView().isRequired())
                        .displaySequence(ontologyField.getUi() != null && ontologyField.getUi().getFormView() != null ? ontologyField.getUi().getFormView().getDisplaySequence() : 0)
                        .build())
                .collect(Collectors.toList());
    }

    private String mapTemplateToPath(String template, boolean isPreview) {
        if ("form".equalsIgnoreCase(template)) {
            if (isPreview) {
                return "scenario/scenario-step-form/scenario-step-form";
            } else {
                return "forms/form-basic/form-basic";
            }
        }

        return null;
    }

    private String mapComponentType(OntologyDto.EntitySchema.FieldSchema ontologyField) {
        if (ontologyField.getUi() != null && ontologyField.getUi().getFormView() != null) {
            return ontologyField.getUi().getFormView().getComponent();
        }
        switch (ontologyField.getType().toLowerCase()) {
            case "boolean": return "checkbox";
            case "date": return "date";
            default: return "text";
        }
    }
}