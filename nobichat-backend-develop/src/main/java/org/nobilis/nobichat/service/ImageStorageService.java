package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tika.Tika;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.model.Image;
import org.nobilis.nobichat.repository.ImageRepository;
import org.nobilis.nobichat.util.validator.entity.EntityValidatorRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageStorageService {

    private final ImageRepository imageRepository;
    private final Tika tika;
    private final EntityValidatorRegistry entityValidatorRegistry;

    @SneakyThrows
    @Transactional
    public Image saveImage(UUID entityId, EntityType entityType, MultipartFile file) {
        validateEntityExists(entityId, entityType);

        Image image = imageRepository.findByEntityIdAndEntityType(entityId, entityType)
                .orElse(new Image());

        String detectedContentType = tika.detect(file.getInputStream());

        if (detectedContentType == null || !detectedContentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Файл не является изображением. Обнаружен тип: " + detectedContentType);
        }

        image.setEntityId(entityId);
        image.setEntityType(entityType);
        image.setContentType(detectedContentType);
        image.setImageData(file.getBytes());
        return imageRepository.save(image);
    }

    @Transactional(readOnly = true)
    public Image getImageOrDefault(UUID entityId, EntityType entityType) {
        validateEntityExists(entityId, entityType);

        return imageRepository.findByEntityIdAndEntityType(entityId, entityType)
                .orElseGet(() -> {
                    EntityType defaultType = switch (entityType) {
                        case user -> EntityType.user_default;
                        case supplier -> EntityType.supplier_default;
                        case supplierContact -> EntityType.supplier_contact_default;
                        default -> entityType;
                    };
                    return imageRepository.findByEntityType(defaultType)
                            .orElseThrow(() -> new ResourceNotFoundException("Дефолтное изображение для " + entityType + " не найдено"));
                });
    }

    private void validateEntityExists(UUID entityId, EntityType entityType) {
        entityValidatorRegistry.getValidator(entityType)
                .ifPresent(validator -> validator.validateExists(entityId));
    }
}
