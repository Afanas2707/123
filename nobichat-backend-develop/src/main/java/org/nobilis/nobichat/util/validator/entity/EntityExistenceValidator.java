package org.nobilis.nobichat.util.validator.entity;

import org.nobilis.nobichat.constants.EntityType;

import java.util.UUID;

/**
 * Интерфейс для валидаторов, проверяющих существование сущности по ID.
 * Каждая реализация будет отвечать за свой тип сущности.
 */
public interface EntityExistenceValidator {

    /**
     * Проверяет, какой тип сущности поддерживает данный валидатор.
     * @return EntityType, который обрабатывается этой реализацией.
     */
    EntityType supportedEntityType();

    /**
     * Проверяет, существует ли сущность с данным ID.
     * Если сущность не найдена, выбрасывает исключение
     * @param entityId ID проверяемой сущности.
     */
    void validateExists(UUID entityId);
}
