package org.nobilis.nobichat.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.nobilis.nobichat.dto.chat.AttachmentDto;
import org.nobilis.nobichat.dto.chat.ChatMessageDto;
import org.nobilis.nobichat.dto.chat.UserChatSessionDto;
import org.nobilis.nobichat.model.Attachment;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.UserChatSession;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Mapper(componentModel = "spring")
public interface HistoryMapper {

    @Mapping(source = "id", target = "sessionId")
    @Mapping(source = "title", target = "sessionTitle")
    @Mapping(source = "lastUpdateDate", target = "lastUpdatedDateTime")
    UserChatSessionDto toSessionDto(UserChatSession session);

    List<UserChatSessionDto> toSessionDtoList(List<UserChatSession> sessions);

    @Mapping(source = "id", target = "promptId")
    @Mapping(source = "creationDate", target = "promptDateTime")
    @Mapping(target = "attachments", source = "attachments")
    ChatMessageDto toMessageDto(ChatMessage message);

    List<ChatMessageDto> toMessageDtoList(List<ChatMessage> messages);

    @Mapping(source = "id", target = "id")
    @Mapping(target = "downloadUrl", ignore = true)
    AttachmentDto toAttachmentDto(Attachment attachment);

    List<AttachmentDto> toAttachmentDtoList(List<Attachment> attachments);

    @AfterMapping
    default void afterMappingAttachmentToDto(Attachment attachment, @MappingTarget AttachmentDto dto) {
        if (attachment.getId() != null) {
            ChatMessage message = attachment.getChatMessage();
            if (message != null && message.getSession() != null) {
                String url = String.format("/chat/sessions/%s/prompts/%s/attachments/%s",
                        message.getSession().getId(),
                        message.getId(),
                        attachment.getId()
                );
                dto.setDownloadUrl(url);
            }
        }
    }

    default LocalDateTime map(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}