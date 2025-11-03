package org.nobilis.nobichat.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageListDtoV2 {

    @Schema(description = "Полная история сообщений в сессии, отсортированная по времени.")
    private List<ChatMessageDtoV2> promptsHistory;
}