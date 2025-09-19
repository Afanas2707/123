package org.nobilis.nobichat.util.validator.entity;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.repository.SupplierRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupplierExistenceValidator implements EntityExistenceValidator {

    private final SupplierRepository supplierRepository;

    @Override
    public EntityType supportedEntityType() {
        return EntityType.supplier;
    }

    @Override
    public void validateExists(UUID entityId) {
        if (!supplierRepository.existsById(entityId)) {
            throw new ResourceNotFoundException("Поставщик с id: " + entityId + " не найден");
        }
    }
}
