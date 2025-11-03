package org.nobilis.nobichat.util.validator.entity;

import jakarta.annotation.PostConstruct;
import org.nobilis.nobichat.constants.EntityType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EntityValidatorRegistry {

    private final List<EntityExistenceValidator> validators;
    private final Map<EntityType, EntityExistenceValidator> registry = new EnumMap<>(EntityType.class);

    public EntityValidatorRegistry(List<EntityExistenceValidator> validators) {
        this.validators = validators;
    }

    @PostConstruct
    public void init() {
        for (EntityExistenceValidator validator : validators) {
            registry.put(validator.supportedEntityType(), validator);
        }
    }

    public Optional<EntityExistenceValidator> getValidator(EntityType entityType) {
        return Optional.ofNullable(registry.get(entityType));
    }
}
