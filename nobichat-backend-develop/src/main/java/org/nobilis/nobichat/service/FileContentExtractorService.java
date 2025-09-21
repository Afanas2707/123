package org.nobilis.nobichat.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Service
@Slf4j
public class FileContentExtractorService {

    private static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOC_MIME_TYPE = "application/msword";

    /**
     * Извлекает текст из Word-документа (.doc или .docx).
     *
     * @param file Загруженный пользователем файл.
     * @return Извлеченный текст.
     * @throws IOException              если происходит ошибка чтения файла.
     * @throws IllegalArgumentException если файл не является Word-документом.
     */
    public String extractTextFromWord(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        log.info("Попытка извлечь текст из файла '{}' с Content-Type: {}", file.getOriginalFilename(), contentType);

        if (Objects.equals(contentType, DOCX_MIME_TYPE)) {
            return extractTextFromDocx(file.getInputStream());
        } else if (Objects.equals(contentType, DOC_MIME_TYPE)) {
            return extractTextFromDoc(file.getInputStream());
        } else {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                if (originalFilename.toLowerCase().endsWith(".docx")) {
                    return extractTextFromDocx(file.getInputStream());
                }
                if (originalFilename.toLowerCase().endsWith(".doc")) {
                    return extractTextFromDoc(file.getInputStream());
                }
            }
            throw new IllegalArgumentException("Неподдерживаемый тип файла. Пожалуйста, загрузите файл .doc или .docx.");
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extractTextFromDoc(InputStream inputStream) throws IOException {
        try (WordExtractor extractor = new WordExtractor(inputStream)) {
            return extractor.getText();
        }
    }
}