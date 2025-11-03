package org.nobilis.nobichat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PromptsHistoryDto {

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PromptsHistoryResponseDto {
        private List<PromptHistoryDto> promptsHistory;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PromptHistoryDto {
        private UUID promptId;
        private UserRequestDto userRequest;
        private NobichatResponseDto nobichatResponse;
        private Instant promptDateTime;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserRequestDto {
        private String content;
        private List<AttachmentDto> attachments;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NobichatResponseDto {
        private String content;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AttachmentDto {
        private UUID id;
        private String originalFileName;
        private String contentType;
        private Long fileSize;
        private String downloadUrl;
    }
}
