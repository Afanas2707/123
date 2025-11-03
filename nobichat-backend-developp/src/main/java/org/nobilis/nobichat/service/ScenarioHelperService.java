package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.UiSchemaDtos;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.model.UserChatSession;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScenarioHelperService {

    private final UiSchemaBuilderService uiSchemaBuilder;

    public ChatResponseDto createResponseForStep(UserChatSession session, ScenarioDefinition scenarioDefinition, ScenarioDefinition.ScenarioStep step, UUID sourceId, boolean isPreview) {
        String message;
        UiSchemaDtos.UiSchemaDto uiSchema = null;

        if (step == null) {
            session.setActiveScenarioId(null);
            session.setCurrentStepName(null);
            message = "Сценарий успешно завершен!";
        } else {
            uiSchema = uiSchemaBuilder.buildFromStep(scenarioDefinition, step, sourceId, isPreview);
            message = uiSchema.getView().getStepTitle();
        }

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .message(message)
                .uiSchema(uiSchema)
                .build();
    }

    public ChatResponseDto createConstructorResponse(UserChatSession session, String message, String markdown, ScenarioDefinition definition) {

        UiSchemaDtos.StateMarkdownDto stateMarkdown = UiSchemaDtos.StateMarkdownDto.builder()
                .source(markdown)
                .build();

        UiSchemaDtos.ViewDto markdownView = UiSchemaDtos.ViewDto.builder()
                .stepTemplate("frames/text-markdown")
                .stateMarkdown(stateMarkdown)
                .build();

        String scenarioName = (definition != null && StringUtils.hasText(definition.getName()))
                ? definition.getName()
                : "Создание нового сценария";

        String scenarioDescription = (definition != null && StringUtils.hasText(definition.getDescription()))
                ? definition.getDescription()
                : "Укажите основную информацию о сценарии";

        UiSchemaDtos.UiSchemaDto uiSchema = UiSchemaDtos.UiSchemaDto.builder()
                .scenarioName(scenarioName)
                .scenarioDescription(scenarioDescription)
                .view(markdownView)
                .build();

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .message(message)
                .uiSchema(uiSchema)
                .build();
    }
}