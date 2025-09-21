package org.nobilis.nobichat.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class NavigationResponseDto {
    private ChatResponseDto currentState;
    private NavigationInfoDto navigationInfo;

    @Data
    @Builder
    public static class NavigationInfoDto {
        private UUID activePromptId;
        private Boolean canNavigateBack;
        private Boolean canNavigateForward;
    }
}
