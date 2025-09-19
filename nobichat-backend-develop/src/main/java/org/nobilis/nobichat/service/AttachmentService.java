package org.nobilis.nobichat.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.model.Attachment;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.repository.AttachmentRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final LocalStorageService fileStorageService;

    /**
     * Внутренний класс-контейнер для передачи данных в контроллер.
     */
    @Getter
    @RequiredArgsConstructor
    public static class DownloadableAttachment {
        private final String originalFileName;
        private final String contentType;
        private final Long fileSize;
        private final Resource resource;
    }

    @Transactional(readOnly = true)
    public DownloadableAttachment getAttachmentForDownload(UUID sessionId, UUID promptId, UUID attachmentId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Attachment attachment = attachmentRepository.findByIdAndChatMessageIdAndChatMessageSessionId(attachmentId, promptId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Файл с id %s не найден в запросе %s и сессии %s", attachmentId, promptId, sessionId)
                ));

        ChatMessage message = attachment.getChatMessage();
        if (!message.getSession().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ к чужой сессии запрещён");
        }

        Resource resource = fileStorageService.loadAsResource(attachment.getId().toString());

        return new DownloadableAttachment(
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                resource
        );
    }

    public void saveAttachments(List<MultipartFile> files, ChatMessage chatMessage) {
        if (files == null || files.isEmpty()) {
            return;
        }

        List<Attachment> attachments = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            Attachment attachment = Attachment.builder()
                    .originalFileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .chatMessage(chatMessage)
                    .build();

            attachmentRepository.save(attachment);

            String storageFileName = attachment.getId().toString();
            fileStorageService.save(file, storageFileName);

            attachments.add(attachment);
        }
        chatMessage.setAttachments(attachments);
    }
}
