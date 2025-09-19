package org.nobilis.nobichat.dto.chat;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserChatSessionDto {
    private UUID sessionId;
    private String sessionTitle;
    private LocalDateTime lastUpdatedDateTime;
}
