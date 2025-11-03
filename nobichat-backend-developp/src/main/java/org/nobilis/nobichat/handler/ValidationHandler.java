package org.nobilis.nobichat.handler;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice
public class ValidationHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @Hidden
    public ResponseEntity<GeneralErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                           HttpServletRequest request) {
        String errorMessage = "Ошибка валидации";
        if (ex.getBindingResult().hasErrors()) {
            ObjectError firstError = ex.getBindingResult().getAllErrors().get(0);
            errorMessage = firstError.getDefaultMessage();
        }

        GeneralErrorResponse errorResponse = GeneralErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(errorMessage)
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}
