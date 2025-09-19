package org.nobilis.nobichat.dto.template;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplatesListResponseDto {
    private List<TemplateResponseDto> templates;
}
