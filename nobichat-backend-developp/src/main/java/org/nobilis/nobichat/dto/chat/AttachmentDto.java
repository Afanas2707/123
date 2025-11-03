package org.nobilis.nobichat.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDto {
    private UUID id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String downloadUrl;
    @JsonIgnore
    private Resource resource;
}
