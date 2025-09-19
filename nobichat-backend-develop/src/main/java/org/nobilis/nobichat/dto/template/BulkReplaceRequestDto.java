package org.nobilis.nobichat.dto.template;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BulkReplaceRequestDto {

    @NotNull
    private UUID newTemplateId;
}
