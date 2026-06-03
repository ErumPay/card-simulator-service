package com.erumpay.card_simulator_service.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    protected ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        log.error("handleMissingHeader [{}] : {}", e.getClass().getSimpleName(), e.getMessage());
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return ErrorResponse.toResponseEntity(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("handleMessageNotReadable : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.MESSAGE_NOT_READABLE);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodValidationException.class,
            HandlerMethodValidationException.class
    })
    protected ResponseEntity<ErrorResponse> handleValidationException(Exception e) {
        log.error("handleValidationException [{}] : {}", e.getClass().getSimpleName(), e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    protected ResponseEntity<ErrorResponse> handleDatabaseException(DataAccessException e) {
        log.error("handleDatabaseException : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.DATABASE_ERROR);
    }

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("handleCustomException : {}", e.getErrorCode());
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("handleException : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
