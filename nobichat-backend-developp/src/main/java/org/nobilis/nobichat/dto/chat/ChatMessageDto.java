package org.nobilis.nobichat.dto.chat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ChatMessageDto {
    private UUID promptId;
    private String userRequestText;
    private String aiResponseText;
    private LocalDateTime promptDateTime;
    private UUID templateId;

    private String frontendTemplateKey;
    private String frontendEntityName;
    private UUID frontendSourceId;
    private List<AttachmentDto> attachments;
}
