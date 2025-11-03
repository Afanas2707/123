package org.nobilis.nobichat.dto.chat;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ChatMessageDtoV2 {
    private UUID promptId;
    private UserRequestDto userRequest;
    private NobichatResponseDto nobichatResponse;
    private LocalDateTime promptDateTime;

    @Data
    public static class UserRequestDto {
        private String content;
        private List<AttachmentDto> attachments;
    }

    @Data
    public static class NobichatResponseDto {
        private String content;
        private Boolean hasUiState;
    }
}
