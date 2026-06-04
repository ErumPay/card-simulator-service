package com.erumpay.card_simulator_service.exception;

import lombok.Getter;

// [be] 하지혁 260603 자체 서비스 위반 예외 클래스
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }
}
