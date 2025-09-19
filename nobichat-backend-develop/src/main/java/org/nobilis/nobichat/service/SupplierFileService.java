package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.model.Supplier;
import org.nobilis.nobichat.model.SupplierFile;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.repository.SupplierFileRepository;
import org.nobilis.nobichat.repository.SupplierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierFileService {

    private final SupplierRepository supplierRepository;
    private final SupplierFileRepository supplierFileRepository;
    private final FileStorageService fileStorageService;

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    @Transactional
    public SupplierFile uploadFileForSupplier(UUID supplierId, MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл слишком большой. Максимальный размер - 5 МБ.");
        }

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Поставщик с id: " + supplierId + " не найден"));

        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SupplierFile supplierFile = SupplierFile.builder()
                .supplier(supplier)
                .fileName(file.getOriginalFilename())
                .uploadedByUser(user)
                .description(null)
                .build();

        SupplierFile savedSupplierFile = supplierFileRepository.save(supplierFile);

        fileStorageService.storeFile(file, savedSupplierFile.getId());

        return savedSupplierFile;
    }
}
