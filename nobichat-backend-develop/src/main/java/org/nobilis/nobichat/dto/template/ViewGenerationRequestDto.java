package org.nobilis.nobichat.dto.template;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ViewGenerationRequestDto {
    private String viewId;
    private String entityIdString;
    private UUID sessionId;
}
