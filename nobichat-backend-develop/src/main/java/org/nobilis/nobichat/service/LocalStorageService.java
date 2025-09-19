package org.nobilis.nobichat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class LocalStorageService {

    @Value("${attachments.file.path:/opt/app/files/}")
    private String folderToSaveFile;

    /**
     * Сохраняет файл на диск в заранее подготовленную директорию.
     * @param file Файл для сохранения.
     * @param storageFileName Имя файла, под которым он будет сохранен на диске (например, UUID).
     */
    public void save(MultipartFile file, String storageFileName) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Невозможно сохранить пустой или отсутствующий файл.");
        }
        try {
            Path rootLocation  = Paths.get(folderToSaveFile);

            Path destinationFile = rootLocation.resolve(storageFileName).normalize();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Файл '{}' ({} байт) сохранен как '{}'", file.getOriginalFilename(), file.getSize(), storageFileName);
        } catch (IOException e) {
            log.error("Не удалось сохранить файл '{}' как '{}'. Убедитесь, что директория '{}' существует и доступна для записи.", file.getOriginalFilename(), storageFileName, folderToSaveFile, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при сохранении файла на сервере.", e);
        }
    }

    /**
     * Загружает файл с диска как ресурс.
     * @param storageFileName Имя файла на диске (UUID).
     * @return Ресурс для скачивания.
     */
    public Resource loadAsResource(String storageFileName) {
        try {
            Path file = Paths.get(folderToSaveFile).resolve(storageFileName).normalize();
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                log.error("Запрошен несуществующий или нечитаемый файл: {}", storageFileName);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден.");
            }
        } catch (MalformedURLException e) {
            log.error("Критическая ошибка при формировании URL для файла: {}", storageFileName, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка при доступе к файлу.", e);
        }
    }

    /**
     * Удаляет файл с диска.
     * @param storageFileName Имя файла на диске (UUID).
     */
    public void delete(String storageFileName) {
        try {
            Path fileToDelete = Paths.get(folderToSaveFile).resolve(storageFileName).normalize();
            if (Files.exists(fileToDelete) && !Files.isDirectory(fileToDelete)) {
                FileSystemUtils.deleteRecursively(fileToDelete);
                log.warn("Файл '{}' был удален.", storageFileName);
            }
        } catch (IOException e) {
            log.error("Не удалось удалить файл '{}'. Он может остаться на диске.", storageFileName, e);
        }
    }
}