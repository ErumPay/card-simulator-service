package com.erumpay.card_simulator_service.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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

    // [be] 하지혁 260603 RequestHeader - Idempotency-Key 예외처리
    @ExceptionHandler(MissingRequestHeaderException.class)
    protected ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        log.error("handleMissingHeader [{}] : {}", e.getClass().getSimpleName(), e.getMessage());
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return ErrorResponse.toResponseEntity(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST);
    }

    // [be] 하지혁 260603 RequestHeader - Content-Type 예외처리
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.error("HttpMediaTypeNotSupportedException : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    // [be] 하지혁 260603 RequestBody - 형식 불일치 예외처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("handleMessageNotReadable : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.MESSAGE_NOT_READABLE);
    }

    // [be] 하지혁 260603 RequestBody - 내용 불일치 예외처리
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

    // [be] 하지혁 260603 DataBase 예외처리
    @ExceptionHandler(DataAccessException.class)
    protected ResponseEntity<ErrorResponse> handleDatabaseException(DataAccessException e) {
        log.error("handleDatabaseException : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.DATABASE_ERROR);
    }

    // [be] 하지혁 260603 자체 서비스 위반 예외처리
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("handleCustomException : {}", e.getErrorCode());
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    // [be] 하지혁 260603 예상치 못한 에러 예외처리
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("handleException : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
