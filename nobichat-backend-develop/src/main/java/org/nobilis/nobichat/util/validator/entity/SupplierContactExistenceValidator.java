package org.nobilis.nobichat.util.validator.entity;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.repository.SupplierContactRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupplierContactExistenceValidator implements EntityExistenceValidator {

    private final SupplierContactRepository supplierContactRepository;

    @Override
    public EntityType supportedEntityType() {
        return EntityType.supplierContact;
    }

    @Override
    public void validateExists(UUID entityId) {
        if (!supplierContactRepository.existsById(entityId)) {
            throw new ResourceNotFoundException("Контакт поставщика с id: " + entityId + " не найден");
        }
    }
}
