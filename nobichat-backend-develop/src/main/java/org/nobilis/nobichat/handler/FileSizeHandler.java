package org.nobilis.nobichat.handler;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice
public class FileSizeHandler {

    private static final int MAX_TOTAL_SIZE_IN_MB = 100;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @Hidden
    public ResponseEntity<GeneralErrorResponse> handleMaxSizeException(MaxUploadSizeExceededException exc, HttpServletRequest request) {

        GeneralErrorResponse errorResponse = GeneralErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase())
                .message(String.format("Общий размер загружаемых файлов превышает максимальный лимит в %d MB.", MAX_TOTAL_SIZE_IN_MB))
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
