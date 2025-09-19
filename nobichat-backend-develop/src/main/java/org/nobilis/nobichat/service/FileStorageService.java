package org.nobilis.nobichat.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    @Value("${app.files.path:/opt/app/files/}")
    private String folderToSaveExcelFile;

    public String storeFile(MultipartFile file, UUID supplierFileId) {
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf(".") + 1).toLowerCase();

        String storedFileName = supplierFileId.toString() + fileExtension;


        Path newfilePath = Path.of(folderToSaveExcelFile, storedFileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, newfilePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Файл с идентификатором {} успешно загружен.", supplierFileId);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка загрузки файла: " + e.getMessage());
        }

        return storedFileName;
    }
}
