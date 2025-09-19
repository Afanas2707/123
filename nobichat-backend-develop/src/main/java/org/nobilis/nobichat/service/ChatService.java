package org.nobilis.nobichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.NavigationResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.nobilis.nobichat.repository.UserChatSessionRepository;
import org.nobilis.nobichat.service.intent.UnknownIntentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
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
    private final NavigationService navigationService;
    private final HistoryService historyService;

    @Transactional
    public ChatResponseDto processUserQuery(ChatRequest request, List<MultipartFile> attachments) {
        return processRequestInternal(request, attachments, ChatMode.STRICT);
    }

    @Transactional
    public ChatResponseDto processSoftModeQuery(ChatRequest request, List<MultipartFile> attachments) {
        return processRequestInternal(request, attachments, ChatMode.SOFT);
    }

    private ChatResponseDto processRequestInternal(ChatRequest request, List<MultipartFile> attachments, ChatMode mode) {
        // 1. Создание или получение сессии
        UserChatSession session = getOrCreateSession(request.getSessionId(), request.getMessage());

        // 2. Если есть навигация в прошлое, переписываем историю
        historyService.rewriteHistoryIfInPast(session);

        // 3. Инициализация объекта ChatMessage и его сохранение
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSession(session);
        chatMessage.setUserRequestText(request.getMessage());
        chatMessage.setMode(mode);
        messageRepository.save(chatMessage);

        // 4. Сохранение вложений, если есть
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        if (hasAttachments) {
            attachmentService.saveAttachments(attachments, chatMessage);
        }

        // 5. Извлечение намерения и параметров запроса с помощью LLM
        IntentAndQueryResponse intentResponse = extractIntentAndQuery(request.getMessage(), chatMessage.getId());

        // 6. Сборка контекста для обработчиков
        ChatContext context = ChatContext.builder()
                .request(request)
                .attachments(attachments)
                .chatMode(mode)
                .session(session)
                .chatMessage(chatMessage)
                .currentUiMessage(session.getCurrentUiMessage())
                .build();

        // 7. Диспетчеризация запроса к соответствующему обработчику намерений
        IntentHandler handler = handlerRegistry.getHandler(intentResponse.getIntent())
                .orElseGet(() -> new UnknownIntentHandler(session.getId(),
                        (session.getCurrentUiMessage() != null && session.getCurrentUiMessage().getTemplate() != null)
                                ? session.getCurrentUiMessage().getTemplate().getSchema()
                                : null)); // Fallback для UnknownIntentHandler

        ChatResponseDto response = handler.handle(intentResponse, context);

        chatMessage.setAiResponseText(response.getMessage());
        messageRepository.save(chatMessage);

        ChatMessage finalActiveMessage;
        if (chatMessage.getTemplate() != null) {
            session.setCurrentUiMessage(chatMessage);
            sessionRepository.save(session);
            finalActiveMessage = chatMessage;
        } else {
            finalActiveMessage = session.getCurrentUiMessage();
        }

        NavigationResponseDto.NavigationInfoDto navInfo = navigationService.calculateNavigationInfo(session.getId(), finalActiveMessage);
        response.setNavigationInfo(navInfo);

        return response;
    }

    private UserChatSession getOrCreateSession(UUID sessionId, String userQuery) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (sessionId != null) {
            UserChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found with id: " + sessionId));
            if (!session.getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to this session is denied");
            }
            return session;
        } else {
            UserChatSession newSession = UserChatSession.builder()
                    .user(user)
                    .title(generateTitleFromQuery(userQuery))
                    .build();
            return sessionRepository.save(newSession);
        }
    }

    private IntentAndQueryResponse extractIntentAndQuery(String userQuery, UUID messageId) {
        String prompt = llmPromptService.buildQueryExtractionPrompt(userQuery);
        LLMResponseDto llmResponse = llmPromptService.sendPromptToLlm(prompt, null, true, messageId);

        String rawContent = llmResponse.getContent();
        if (rawContent == null || rawContent.isBlank()) {
            log.error("LLM вернула пустой ответ для извлечения намерения. Запрос: '{}'", userQuery);
            return new IntentAndQueryResponse("UNKNOWN", null, null, null, null, null);
        }

        String cleanJson = llmPromptService.sanitizeLlmJsonResponse(rawContent);

        try {
            IntentAndQueryResponse response = new ObjectMapper().readValue(cleanJson, IntentAndQueryResponse.class);
            log.info("Намерение LLM распознано: intent={}, entity={}, sourceUrl={}",
                    response.getIntent(), response.getEntity(), response.getSourceUrl());
            return response;
        } catch (JsonProcessingException e) {
            log.error("Ошибка при парсинге LLM-ответа для извлечения намерения. Сырой ответ: '{}', Ошибка: {}", rawContent, e.getMessage(), e);
            return new IntentAndQueryResponse("UNKNOWN", null, null, null, null, null);
        }
    }

    private String generateTitleFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return "Новый диалог";
        }
        String title = Arrays.stream(query.split("\\s+"))
                .limit(5)
                .collect(Collectors.joining(" "));
        if (query.length() > title.length()) {
            title += "...";
        }
        return title.substring(0, Math.min(title.length(), 255));
    }
}