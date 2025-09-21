package org.nobilis.nobichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.dto.chat.AddChatMessageRequestDto;
import org.nobilis.nobichat.dto.chat.AttachmentDto;
import org.nobilis.nobichat.dto.chat.ChatMessageDto;
import org.nobilis.nobichat.dto.chat.ChatMessageDtoV2;
import org.nobilis.nobichat.dto.chat.ChatMessageListDto;
import org.nobilis.nobichat.dto.chat.ChatMessageListDtoV2;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.NavigationResponseDto;
import org.nobilis.nobichat.dto.chat.UserChatSessionDto;
import org.nobilis.nobichat.dto.chat.UserChatSessionListDto;
import org.nobilis.nobichat.dto.template.ViewGenerationRequestDto;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.mapper.HistoryMapper;
import org.nobilis.nobichat.model.Attachment;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.nobilis.nobichat.repository.TemplateRepository;
import org.nobilis.nobichat.repository.UserChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final UserChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final HistoryMapper historyMapper;
    private final TemplateRepository templateRepository;
    private final AttachmentService attachmentService;
    private final NavigationService navigationService;
    private final DynamicViewGeneratorService dynamicViewGeneratorService;
    private final OntologyService ontologyService;

    @Transactional(readOnly = true)
    public UserChatSessionListDto getSessionsForCurrentUser() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<UserChatSession> sessions = sessionRepository.findByUserOrderByLastUpdateDateDesc(user);
        List<UserChatSessionDto> dtoList = historyMapper.toSessionDtoList(sessions);

        return new UserChatSessionListDto(dtoList);
    }

    @Transactional(readOnly = true)
    public Optional<NavigationResponseDto> getActiveUiState(UUID sessionId) {
        UserChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена."));

        ChatMessage activeMessage = session.getCurrentUiMessage();

        if (activeMessage == null || activeMessage.getTemplate() == null) {
            return Optional.empty();
        }

        NavigationResponseDto.NavigationInfoDto navInfoDto =
                navigationService.calculateNavigationInfo(sessionId, activeMessage);

        ChatResponseDto currentStateDto = new ChatResponseDto(
                sessionId,
                activeMessage.getTemplate().getSchema(),
                activeMessage.getAiResponseText(),
                navInfoDto
        );

        return Optional.of(new NavigationResponseDto(currentStateDto, navInfoDto));
    }

    public ChatMessageListDtoV2 getMessagesForSessionV2(UUID sessionId) {
        UserChatSession session = sessionRepository.findByIdWithMessages(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена."));

        List<ChatMessage> messages = session.getMessages();

        if (messages == null || messages.isEmpty()) {
            return new ChatMessageListDtoV2(Collections.emptyList());
        }

        List<ChatMessage> messagesWithAttachments = messageRepository.findWithAttachments(messages);

        List<ChatMessage> sortedMessages = messagesWithAttachments.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreationDate))
                .collect(Collectors.toList());

        List<ChatMessageDtoV2> dtoList = sortedMessages.stream()
                .map(this::mapMessageToDto)
                .collect(Collectors.toList());

        return new ChatMessageListDtoV2(dtoList);
    }

    /**
     * Преобразует сущность ChatMessage в DTO с необходимой вложенной структурой.
     */
    private ChatMessageDtoV2 mapMessageToDto(ChatMessage message) {
        ChatMessageDtoV2 dto = new ChatMessageDtoV2();
        dto.setPromptId(message.getId());

        if (message.getCreationDate() != null) {
            dto.setPromptDateTime(message.getCreationDate().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }

        ChatMessageDtoV2.UserRequestDto userRequest = new ChatMessageDtoV2.UserRequestDto();
        userRequest.setContent(message.getUserRequestText());
        List<AttachmentDto> attachmentDtos = message.getAttachments().stream()
                .map(this::mapAttachmentToDto)
                .collect(Collectors.toList());
        userRequest.setAttachments(attachmentDtos);
        dto.setUserRequest(userRequest);

        ChatMessageDtoV2.NobichatResponseDto nobichatResponse = new ChatMessageDtoV2.NobichatResponseDto();
        nobichatResponse.setContent(message.getAiResponseText());

        nobichatResponse.setHasUiState(message.getTemplate() != null);

        dto.setNobichatResponse(nobichatResponse);

        return dto;
    }

    private AttachmentDto mapAttachmentToDto(Attachment attachment) {
        String downloadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/chat/sessions/{sessionId}/prompts/{promptId}/attachments/{attachmentId}")
                .buildAndExpand(
                        attachment.getChatMessage().getSession().getId(),
                        attachment.getChatMessage().getId(),
                        attachment.getId()
                )
                .toUriString();

        AttachmentDto dto = new AttachmentDto();
        dto.setId(attachment.getId());
        dto.setOriginalFileName(attachment.getOriginalFileName());
        dto.setFileSize(attachment.getFileSize());
        dto.setContentType(attachment.getContentType());
        dto.setDownloadUrl(downloadUrl);

        return dto;
    }

    @Transactional(readOnly = true)
    public ChatMessageListDto getMessagesForSession(UUID sessionId) {

        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreationDateAsc(sessionId);

        List<ChatMessageDto> dtoList = messages.stream()
                .map(message -> {

                    if (message.getCreationDate() == null) {
                        message.getCreationDate();
                    }

                    ChatMessageDto dto = historyMapper.toMessageDto(message);

                    if (message.getTemplate() != null) {
                        dto.setResponseSchemaSnapshot(message.getTemplate().getSchema());
                    } else {
                        dto.setResponseSchemaSnapshot(null);
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        return new ChatMessageListDto(dtoList);
    }

    @Transactional
    public void deleteUserChatSession(UUID sessionId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Чат с ID " + sessionId + " не найден."));

        if (!session.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Разрешено удалять только свои чаты.");
        }

        sessionRepository.delete(session);
    }

    @Transactional
    public ChatMessageDto addMessageToSession(AddChatMessageRequestDto request, List<MultipartFile> attachments) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserChatSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена id: " + request.getSessionId()));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Запрещён доступ к чужой сессии");
        }

        Template newTemplate = Template.builder()
                .mode(request.getMode())
                .schema(request.getTemplateSchema())
                .build();

        Template templateToAssociate = templateRepository.save(newTemplate);

        ChatMessage chatMessage = ChatMessage.builder()
                .session(session)
                .userRequestText(request.getUserRequestText())
                .aiResponseText(request.getAiResponseText())
                .mode(request.getMode())
                .template(templateToAssociate)
                .build();

        chatMessage = messageRepository.save(chatMessage);
        attachmentService.saveAttachments(attachments, chatMessage);

        session.setCurrentUiMessage(chatMessage);
        sessionRepository.save(session);

        return historyMapper.toMessageDto(chatMessage);
    }

    @Transactional
    public void rewriteHistoryIfInPast(UserChatSession session) {
        ChatMessage currentUiMessage = session.getCurrentUiMessage();

        if (currentUiMessage != null) {
            List<ChatMessage> messagesToDelete = messageRepository.findBySessionAndCreationDateAfter(session, currentUiMessage.getCreationDate());

            if (!messagesToDelete.isEmpty()) {
                log.warn("Обнаружена перезапись истории в сессии {}. Текущий узел: {}. Удаляется {} последующих сообщений.",
                        session.getId(), currentUiMessage.getId(), messagesToDelete.size());

                messageRepository.deleteAllInBatch(messagesToDelete);

                log.info("История сессии {} успешно усечена.", session.getId());
            }
        }
    }

    @Transactional
    public ChatResponseDto generateAndRecordView(ViewGenerationRequestDto request) {
        UUID sessionId = request.getSessionId();
        String viewId = request.getViewId();
        String entityIdString = request.getEntityIdString();

        if (sessionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для записи в историю обязателен 'X-Chat-Session-Id'.");
        }

        UserChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена: " + sessionId));

        rewriteHistoryIfInPast(session);

        log.info("Генерация UI-схемы для viewId '{}' в сессии '{}'", viewId, sessionId);
        JsonNode generatedSchema = generateSchemaFromViewId(viewId, entityIdString);

        Template newTemplate = Template.builder()
                .mode(ChatMode.STRICT)
                .schema(generatedSchema)
                .build();
        templateRepository.save(newTemplate);

        ChatMessage newChatMessage = ChatMessage.builder()
                .session(session)
                .userRequestText(null)
                .aiResponseText(null)
                .mode(ChatMode.STRICT)
                .template(newTemplate)
                .build();
        messageRepository.save(newChatMessage);

        session.setCurrentUiMessage(newChatMessage);
        sessionRepository.save(session);
        log.info("В сессии {} создано и активировано новое UI-состояние {} для viewId {}", sessionId, newChatMessage.getId(), viewId);

        NavigationResponseDto.NavigationInfoDto navInfo =
                navigationService.calculateNavigationInfo(sessionId, newChatMessage);

        return new ChatResponseDto(
                sessionId,
                generatedSchema,
                null,
                navInfo
        );
    }

    /**
     * Приватный метод для инкапсуляции логики генерации схемы.
     */
    private JsonNode generateSchemaFromViewId(String viewId, String entityIdString) {
        String[] parts = viewId.split("\\.");
        if (parts.length != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный формат viewId. Ожидается: entityName.viewCategory.viewFormat (например, supplier.list.view)");
        }
        String entityName = parts[0];
        String viewCategory = parts[1];
        String viewFormat = parts[2];

        if (!ontologyService.entityExists(entityName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сущность '" + entityName + "' не найдена в онтологии.");
        }

        JsonNode generatedSchema;

        if ("list".equalsIgnoreCase(viewCategory) && "view".equalsIgnoreCase(viewFormat)) {
            generatedSchema = dynamicViewGeneratorService.generateListViewStrict(entityName, null, null);
        } else if ("edit".equalsIgnoreCase(viewCategory) && "form".equalsIgnoreCase(viewFormat)) {
            if (!StringUtils.hasText(entityIdString)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для FormView ('" + viewId + "') обязателен параметр 'entityId'.");
            }
            UUID entityId;
            try {
                entityId = UUID.fromString(entityIdString);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный формат entityId: " + entityIdString, e);
            }
            generatedSchema = dynamicViewGeneratorService.generateFormView(entityName, entityId);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная комбинация viewCategory.viewFormat: '" + viewCategory + "." + viewFormat + "'");
        }

        if (generatedSchema == null) {
            log.error("DynamicViewGeneratorService вернул null для viewId: {}. Проверьте логику генерации.", viewId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сгенерировать UI-шаблон для: " + viewId);
        }

        return generatedSchema;
    }

    /**
     * Приватный метод для форматирования текста "запроса пользователя" для системных сообщений.
     */
    private String formatUserRequestText(String viewId, String entityIdString) {
        String baseText = "Переход к представлению: " + viewId;
        if (StringUtils.hasText(entityIdString)) {
            return baseText + " (ID сущности: " + entityIdString + ")";
        }
        return baseText;
    }
}
