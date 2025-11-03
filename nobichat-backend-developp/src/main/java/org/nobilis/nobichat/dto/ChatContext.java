package org.nobilis.nobichat.dto;

import lombok.Builder;
import lombok.Data;
import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.UserChatSession;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
public class ChatContext {
    private ChatRequest request;
    private List<MultipartFile> attachments;
    private ChatMode chatMode;
    private UserChatSession session;
    private ChatMessage chatMessage; // Текущее сообщение, которое мы обрабатываем
    private ChatMessage currentUiMessage; // Сообщение, соответствующее текущему UI
}
