package org.nobilis.nobichat.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserChatSessionListDto {
    private List<UserChatSessionDto> sessions;
}