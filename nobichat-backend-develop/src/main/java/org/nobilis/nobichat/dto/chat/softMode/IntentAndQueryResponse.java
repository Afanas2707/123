package org.nobilis.nobichat.dto.chat.softMode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nobilis.nobichat.dto.entities.EntitiesSearchRequestDto;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentAndQueryResponse {
    private String intent;
    private String entity; // "supplier", "order", "user"
    private EntitiesSearchRequestDto.QueryDto query; // Может быть null
    private String sourceUrl; // Используется для CREATE_ENTITY_FROM_URL
    private String inn; // Используется для GET_INN_REPORT
    private Integer itemIndex; // НОВОЕ: Номер элемента в списке (например, 3 из "Открой клиента 3")
}