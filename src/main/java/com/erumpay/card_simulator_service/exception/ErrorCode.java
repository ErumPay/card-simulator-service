package com.erumpay.card_simulator_service.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "SIM-CORE-001", "SIMULATOR_INVALID_REQUEST", "잘못된 요청입니다."),
    IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "SIM-CORE-002", "SIMULATOR_IDEMPOTENCY_KEY_MISSING", "멱등성 키가 누락되었습니다."),
    MESSAGE_NOT_READABLE(HttpStatus.BAD_REQUEST, "SIM-CORE-003", "SIMULATOR_MESSAGE_NOT_READABLE", "요청 본문을 읽을 수 없습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SIM-CORE-900", "SIMULATOR_INTERNAL_ERROR", "시뮬레이터 내부 오류가 발생했습니다."),
    RESPONSE_CODE_MAPPING_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "SIM-CORE-901", "SIMULATOR_RESPONSE_CODE_MAPPING_MISSING", "응답코드 매핑이 누락되었습니다."),
    ENCRYPTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SIM-CORE-902", "SIMULATOR_ENCRYPTION_ERROR", "암복호화 처리에 실패했습니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SIM-CORE-903", "SIMULATOR_DATABASE_ERROR", "데이터베이스 처리에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String reason;
    private final String message;
}
