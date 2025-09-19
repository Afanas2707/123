package org.nobilis.nobichat.dto.supplier;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPaginatedResponseDto {
    private long totalElements;
    private int totalPages;
    private List<SupplierListDto> suppliers;
}