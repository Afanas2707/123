package org.nobilis.nobichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.UiSchemaDtos;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Scenario;
import org.nobilis.nobichat.model.ScenarioDraft;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.nobilis.nobichat.repository.ScenarioDraftRepository;
import org.nobilis.nobichat.repository.ScenarioRepository;
import org.nobilis.nobichat.repository.UserChatSessionRepository;
import org.nobilis.nobichat.service.intent.IntentHandler;
import org.nobilis.nobichat.service.intent.IntentHandlerRegistry;
import org.nobilis.nobichat.service.intent.UnknownIntentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final IntentHandlerRegistry handlerRegistry;
    private final LlmPromptService llmPromptService;
    private final UserChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper;
    private final ScenarioConstructorService constructorService;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioDraftRepository draftRepository;
    private final ScenarioFormatterService formatterService;
    private final ScenarioHelperService scenarioHelperService;
    private final DynamicEntityQueryService dynamicEntityQueryService;

    public ChatResponseDto processUserQuery(ChatRequest request, List<MultipartFile> attachments) {
        UserChatSession session = getOrCreateSession(request.getSessionId(), request.getMessage());

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSession(session);
        chatMessage.setUserRequestText(request.getMessage());
        messageRepository.save(chatMessage);

        if (attachments != null && !attachments.isEmpty()) {
            attachmentService.saveAttachments(attachments, chatMessage);
        }

        ChatResponseDto response = getResponse(request, session, chatMessage);

        updateStateAfterResponse(response, session, chatMessage);

        return response;
    }

    private ChatResponseDto getResponse(ChatRequest request, UserChatSession session, ChatMessage chatMessage) {

        IntentAndQueryResponse intentResponse;

        if (Boolean.TRUE.equals(session.getIsInPreviewMode())) {
            log.info("Сессия {} в режиме просмотра.", session.getId());
            String prompt = llmPromptService.buildPreviewModeExtractionPrompt(request.getMessage());
            log.info("Промпт: {}", prompt);
            intentResponse = extractIntentAndQuery(prompt, chatMessage.getId());

            log.info("Определили интент: {}", intentResponse);

            if (!StringUtils.hasText(intentResponse.getIntent())) {
                String isEditPrompt = llmPromptService.buildIsEditCommandPrompt(request.getMessage());
                String isEditResponseJson = llmPromptService.sendPromptToLlm(isEditPrompt, null, false, chatMessage.getId()).getContent();

                String sanitizedJson = llmPromptService.sanitizeLlmJsonResponse(isEditResponseJson);

                boolean isEditCommand = false;
                try {
                    isEditCommand = objectMapper.readTree(sanitizedJson).get("isEditCommand").asBoolean();
                } catch (Exception e) {
                    log.warn("Не удалось распознать, является ли команда редактированием. Считаем, что нет.");
                }

                if (isEditCommand) {
                    log.info("Распознана команда редактирования в режиме просмотра.");
                    constructorService.processUserInput(request, session, chatMessage);
                    ScenarioDraft updatedDraft = draftRepository.findById(session.getActiveDraftId()).orElseThrow();

                    ScenarioDefinition.ScenarioStep currentPreviewStep = updatedDraft.getDefinition().getSteps().stream()
                            .filter(s -> s.getName().equals(session.getCurrentPreviewStepName()))
                            .findFirst()
                            .orElse(updatedDraft.getDefinition().getSteps().stream().min(Comparator.comparingInt(ScenarioDefinition.ScenarioStep::getStepIndex)).orElse(null));

                    if (currentPreviewStep != null) {
                        session.setCurrentPreviewStepName(currentPreviewStep.getName());
                        return scenarioHelperService.createResponseForStep(session, updatedDraft.getDefinition(), currentPreviewStep, null, true);
                    } else {
                        session.setIsInPreviewMode(false);
                        return scenarioHelperService.createConstructorResponse(session, "Шаги были изменены. Возврат в режим редактирования.",
                                formatterService.formatScenarioAsMarkdown(updatedDraft.getDefinition()), updatedDraft.getDefinition());
                    }
                } else {
                    log.info("В режиме просмотра получена нераспознанная команда (не редактирование).");
                    ScenarioDraft draft = draftRepository.findById(session.getActiveDraftId()).orElseThrow();
                    ScenarioDefinition.ScenarioStep currentStep = draft.getDefinition().getSteps().stream()
                            .filter(s -> s.getName().equals(session.getCurrentPreviewStepName())).findFirst().orElseThrow();

                    ChatResponseDto response = scenarioHelperService.createResponseForStep(session, draft.getDefinition(), currentStep, null, true);
                    response.setMessage("Команда не распознана. Используйте 'дальше', 'назад' или опишите, что нужно изменить.");
                    return response;
                }
            }
            IntentHandler handler = handlerRegistry.getHandler(intentResponse.getIntent()).orElseGet(UnknownIntentHandler::new);
            return handler.handle(intentResponse, request, session, chatMessage);

        } else if (Boolean.TRUE.equals(session.getIsInConstructorMode())) {
            log.debug("Сессия {} в режиме конструктора.", session.getId());
            String prompt = llmPromptService.buildConstructorModeExtractionPrompt(request.getMessage());
            intentResponse = extractIntentAndQuery(prompt, chatMessage.getId());

            if (!StringUtils.hasText(intentResponse.getIntent())) {
                return constructorService.processUserInput(request, session, chatMessage);
            }
            IntentHandler handler = handlerRegistry.getHandler(intentResponse.getIntent()).orElseGet(UnknownIntentHandler::new);
            return handler.handle(intentResponse, request, session, chatMessage);

        } else if (session.getActiveScenarioId() != null) {
            log.debug("Сессия {} в режиме исполнения сценария {}.", session.getId(), session.getActiveScenarioId());

            String prompt = llmPromptService.buildExecutionModeExtractionPrompt(request.getMessage());
            intentResponse = extractIntentAndQuery(prompt, chatMessage.getId());

            switch (intentResponse.getIntent()) {
                case "NAVIGATE_EXECUTION":
                    return handlerRegistry.getHandler("NAVIGATE_EXECUTION").get().handle(intentResponse, request, session, chatMessage);

                case "SAVE_SCENARIO_DATA":
                    return handlerRegistry.getHandler("SAVE_SCENARIO_DATA").get().handle(intentResponse, request, session, chatMessage);

                default:
                    log.info("В режиме исполнения получена нераспознанная команда, остаемся на текущем шаге.");
                    Scenario scenario = scenarioRepository.findById(session.getActiveScenarioId()).orElseThrow();
                    ScenarioDefinition.ScenarioStep currentStep = scenario.getDefinition().getSteps().stream()
                            .filter(s -> s.getName().equals(session.getCurrentStepName())).findFirst().orElseThrow();

                    UUID sourceId = null;
                    if ("form".equalsIgnoreCase(currentStep.getTemplate())) {
                        sourceId = dynamicEntityQueryService.findLastCreatedEntityId(currentStep.getEntity()).orElse(null);
                    }

                    ChatResponseDto response = scenarioHelperService.createResponseForStep(session, scenario.getDefinition(), currentStep, sourceId, false);
                    response.setMessage("Команда не распознана. Используйте 'Продолжить', 'Назад' или 'Сохранить'.");
                    return response;
            }

        } else {
            log.debug("Сессия {} в режиме свободного диалога.", session.getId());
            String prompt = llmPromptService.buildFreeModeExtractionPrompt(request.getMessage());
            intentResponse = extractIntentAndQuery(prompt, chatMessage.getId());
            IntentHandler handler = handlerRegistry.getHandler(intentResponse.getIntent()).orElseGet(UnknownIntentHandler::new);
            return handler.handle(intentResponse, request, session, chatMessage);
        }
    }

    /**
     * Сохраняет "слепок" ответа и обновляет состояние сессии.
     */
    private void updateStateAfterResponse(ChatResponseDto response, UserChatSession session, ChatMessage chatMessage) {
        if (response == null) {
            log.warn("Получен пустой ответ, состояние не будет обновлено.");
            return;
        }

        chatMessage.setResponseDto(response);

        chatMessage.setAiResponseText(response.getMessage());
        messageRepository.save(chatMessage);

        if (response.getUiSchema() != null) {
            session.setCurrentUiMessage(chatMessage);
        }
        sessionRepository.save(session);
    }

    private UserChatSession getOrCreateSession(UUID sessionId, String userQuery) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserChatSession session;

        if (sessionId != null) {
            session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found with id: " + sessionId));
            if (!session.getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to this session is denied");
            }
        } else {
            UserChatSession newSession = UserChatSession.builder()
                    .user(user)
                    .title(generateTitleFromQuery(userQuery))
                    .build();
            session = sessionRepository.save(newSession);
        }

        RequestContextHolder.currentRequestAttributes()
                .setAttribute("CURRENT_USER_CHAT_SESSION_ID", session.getId(), RequestAttributes.SCOPE_REQUEST);

        return session;
    }

    private IntentAndQueryResponse extractIntentAndQuery(String prompt, UUID messageId) {
        LLMResponseDto llmResponse = llmPromptService.sendPromptToLlm(prompt, null, true, messageId);
        String rawContent = llmResponse.getContent();
        if (rawContent == null || rawContent.isBlank() || rawContent.trim().equals("{}")) {
            log.debug("LLM вернула пустой ответ для извлечения намерения. Считаем, что интент не распознан.");
            return new IntentAndQueryResponse();
        }
        String cleanJson = llmPromptService.sanitizeLlmJsonResponse(rawContent);
        try {
            IntentAndQueryResponse response = objectMapper.readValue(cleanJson, IntentAndQueryResponse.class);
            log.info("Намерение LLM распознано: intent={}, scenarioName={}, direction={}", response.getIntent(), response.getScenarioName(), response.getDirection());
            return response;
        } catch (JsonProcessingException e) {
            log.error("Ошибка при парсинге LLM-ответа: '{}'. Ошибка: {}", rawContent, e.getMessage());
            return new IntentAndQueryResponse();
        }
    }

    private String generateTitleFromQuery(String query) {
        if (query == null || query.isBlank()) { return "Новый диалог"; }
        String title = Arrays.stream(query.split("\\s+")).limit(5).collect(Collectors.joining(" "));
        if (query.length() > title.length()) { title += "..."; }
        return title.substring(0, Math.min(title.length(), 255));
    }

    @Transactional(readOnly = true)
    public ChatResponseDto resumeSession(UUID sessionId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена: " + sessionId));

        if (!user.getId().equals(session.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ к этой сессии запрещен");
        }

        log.info("Возобновление сессии {} для пользователя {}", sessionId, user.getUsername());

        ChatMessage lastUiMessage = session.getCurrentUiMessage();

        if (lastUiMessage != null && lastUiMessage.getResponseDto() != null) {
            log.debug("Найдено сохраненное состояние UI. Возвращаем слепок.");
            return lastUiMessage.getResponseDto();
        }

        log.debug("Сохраненное состояние UI не найдено. Возвращаем стандартное приветствие.");
        return ChatResponseDto.builder()
                .sessionId(sessionId)
                .message("Вы вернулись в чат. Чем могу помочь?")
                .build();
    }

    public ChatResponseDto rebuildLastState(UserChatSession session) {
        if (Boolean.TRUE.equals(session.getIsInPreviewMode())) {
            ScenarioDraft draft = draftRepository.findById(session.getActiveDraftId()).orElse(null);
            if (draft == null) return ChatResponseDto.builder().sessionId(session.getId()).build();

            ScenarioDefinition.ScenarioStep step = draft.getDefinition().getSteps().stream()
                    .filter(s -> s.getName().equals(session.getCurrentPreviewStepName())).findFirst().orElse(null);
            return scenarioHelperService.createResponseForStep(session, draft.getDefinition(), step, null, true);

        } else if (Boolean.TRUE.equals(session.getIsInConstructorMode())) {
            ScenarioDraft draft = draftRepository.findById(session.getActiveDraftId()).orElse(null);
            if (draft == null) return ChatResponseDto.builder().sessionId(session.getId()).build();

            String markdown = formatterService.formatScenarioAsMarkdown(draft.getDefinition());
            return scenarioHelperService.createConstructorResponse(session, null, markdown, draft.getDefinition());

        } else if (session.getActiveScenarioId() != null) {
            Scenario scenario = scenarioRepository.findById(session.getActiveScenarioId()).orElse(null);
            if (scenario == null) return ChatResponseDto.builder().sessionId(session.getId()).build();

            ScenarioDefinition.ScenarioStep step = scenario.getDefinition().getSteps().stream()
                    .filter(s -> s.getName().equals(session.getCurrentStepName())).findFirst().orElse(null);
            return scenarioHelperService.createResponseForStep(session, scenario.getDefinition(), step, null, false);

        } else {
            return ChatResponseDto.builder().sessionId(session.getId()).build();
        }
    }
}