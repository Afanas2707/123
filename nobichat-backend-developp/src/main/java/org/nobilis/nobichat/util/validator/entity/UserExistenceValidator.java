package org.nobilis.nobichat.util.validator.entity;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserExistenceValidator implements EntityExistenceValidator {

    private final UserRepository userRepository;

    @Override
    public EntityType supportedEntityType() {
        return EntityType.user;
    }

    @Override
    public void validateExists(UUID entityId) {
        if (!userRepository.existsById(entityId)) {
            throw new ResourceNotFoundException("Пользователь с id: " + entityId + " не найден");
        }
    }
}
