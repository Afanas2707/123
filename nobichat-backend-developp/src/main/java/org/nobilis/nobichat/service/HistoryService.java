package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.PromptsHistoryDto;
import org.nobilis.nobichat.dto.chat.UserChatSessionDto;
import org.nobilis.nobichat.dto.chat.UserChatSessionListDto;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.mapper.HistoryMapper;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.nobilis.nobichat.repository.UserChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final UserChatSessionRepository sessionRepository;
    private final HistoryMapper historyMapper;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public UserChatSessionListDto getSessionsForCurrentUser() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<UserChatSession> sessions = sessionRepository.findByUserOrderByLastUpdateDateDesc(user);
        List<UserChatSessionDto> dtoList = historyMapper.toSessionDtoList(sessions);

        return new UserChatSessionListDto(dtoList);
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

    @Transactional(readOnly = true)
    public PromptsHistoryDto.PromptsHistoryResponseDto getSessionHistory(UUID sessionId) {

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreationDateAsc(sessionId);

        List<PromptsHistoryDto.PromptHistoryDto> history = messages.stream()
                .map(this::mapMessageToPromptHistoryDto)
                .collect(Collectors.toList());

        return PromptsHistoryDto.PromptsHistoryResponseDto.builder()
                .promptsHistory(history)
                .build();
    }

    private PromptsHistoryDto.PromptHistoryDto mapMessageToPromptHistoryDto(ChatMessage message) {

        List<PromptsHistoryDto.AttachmentDto> attachmentDtos = message.getAttachments().stream()
                .map(att -> PromptsHistoryDto.AttachmentDto.builder()
                        .id(att.getId())
                        .originalFileName(att.getOriginalFileName())
                        .contentType(att.getContentType())
                        .fileSize(att.getFileSize())
                        .downloadUrl(buildAttachmentDownloadUrl(message.getSession().getId(), message.getId(), att.getId()))
                        .build())
                .collect(Collectors.toList());

        PromptsHistoryDto.UserRequestDto userRequest = PromptsHistoryDto.UserRequestDto.builder()
                .content(message.getUserRequestText())
                .attachments(attachmentDtos)
                .build();

        String aiContent = (message.getResponseDto() != null) ? message.getResponseDto().getMessage() : message.getAiResponseText();
        PromptsHistoryDto.NobichatResponseDto nobichatResponse = PromptsHistoryDto.NobichatResponseDto.builder()
                .content(aiContent)
                .build();

        return PromptsHistoryDto.PromptHistoryDto.builder()
                .promptId(message.getId())
                .userRequest(userRequest)
                .nobichatResponse(nobichatResponse)
                .promptDateTime(message.getCreationDate())
                .build();
    }

    private String buildAttachmentDownloadUrl(UUID sessionId, UUID promptId, UUID attachmentId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/chat/sessions/{sessionId}/prompts/{promptId}/attachments/{attachmentId}")
                .buildAndExpand(sessionId, promptId, attachmentId)
                .toUriString();
    }
}