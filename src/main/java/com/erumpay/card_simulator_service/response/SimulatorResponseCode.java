package com.erumpay.card_simulator_service.response;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SimulatorResponseCode {

    // ===== TOKEN (100번대) =====
    TOKEN_SUCCESS(HttpStatus.OK, "SIM-TOKEN-100", "TOKEN_SUCCESS", "정상 처리되었습니다."),
    TOKEN_NOT_FOUND(HttpStatus.OK, "SIM-TOKEN-101", "TOKEN_NOT_FOUND", "토큰을 찾을 수 없습니다."),
    TOKEN_DUPLICATE(HttpStatus.OK, "SIM-TOKEN-102", "TOKEN_DUPLICATE", "이미 발급된 토큰이 존재합니다."),
    TOKEN_ALREADY_DELETED(HttpStatus.OK, "SIM-TOKEN-103", "TOKEN_ALREADY_DELETED", "이미 삭제된 토큰입니다."),
    TOKEN_ISSUE_NOT_FOUND(HttpStatus.OK, "SIM-TOKEN-104", "TOKEN_ISSUE_NOT_FOUND", "발급 이력을 찾을 수 없습니다."),

    // ===== CARD (200번대) =====
    CARD_SUCCESS(HttpStatus.OK, "SIM-CARD-200", "CARD_SUCCESS", "정상 처리되었습니다."),
    CARD_LOST(HttpStatus.OK, "SIM-CARD-201", "CARD_LOST", "분실 신고된 카드입니다."),
    CARD_EXPIRED(HttpStatus.OK, "SIM-CARD-202", "CARD_EXPIRED", "만료된 카드입니다."),
    CARD_DELETED(HttpStatus.OK, "SIM-CARD-203", "CARD_DELETED", "해지된 카드입니다."),
    CARD_INVALID_PASSWORD(HttpStatus.OK, "SIM-CARD-205", "CARD_INVALID_PASSWORD", "비밀번호가 일치하지 않습니다."),
    CARD_NOT_FOUND(HttpStatus.OK, "SIM-CARD-206", "CARD_NOT_FOUND", "존재하지 않는 카드입니다."),
    CARD_INVALID_EXPIRY(HttpStatus.OK, "SIM-CARD-207", "CARD_INVALID_EXPIRY", "카드 유효기간이 일치하지 않습니다."),
    CARD_INVALID_CVC(HttpStatus.OK, "SIM-CARD-208", "CARD_INVALID_CVC", "CVC가 일치하지 않습니다."),
    CARD_PRODUCT_NOT_FOUND(HttpStatus.OK, "SIM-CARD-209", "CARD_PRODUCT_NOT_FOUND", "카드 상품을 찾을 수 없습니다."),
    CARD_NOT_OWNED(HttpStatus.OK, "SIM-CARD-210", "CARD_NOT_OWNED", "사용자가 해당 카드를 보유하지 않습니다."),

    // ===== PAYMENT (300번대) =====
    PAYMENT_SUCCESS(HttpStatus.OK, "SIM-PAY-300", "PAYMENT_SUCCESS", "정상 처리되었습니다."),
    PAYMENT_LIMIT_EXCEEDED(HttpStatus.OK, "SIM-PAY-301", "PAYMENT_LIMIT_EXCEEDED", "한도를 초과했습니다."),
    PAYMENT_INSUFFICIENT_BALANCE(HttpStatus.OK, "SIM-PAY-302", "PAYMENT_INSUFFICIENT_BALANCE", "잔액이 부족합니다."),
    PAYMENT_CARD_EXPIRED(HttpStatus.OK, "SIM-PAY-303", "PAYMENT_CARD_EXPIRED", "만료된 카드입니다."),
    PAYMENT_CARD_LOST(HttpStatus.OK, "SIM-PAY-304", "PAYMENT_CARD_LOST", "분실 신고된 카드입니다."),
    PAYMENT_CARD_DELETED(HttpStatus.OK, "SIM-PAY-305", "PAYMENT_CARD_DELETED", "해지된 카드입니다."),
    PAYMENT_TOKEN_INVALID(HttpStatus.OK, "SIM-PAY-306", "PAYMENT_TOKEN_INVALID", "결제 토큰이 유효하지 않습니다."),
    PAYMENT_CARD_NOT_FOUND(HttpStatus.OK, "SIM-PAY-307", "PAYMENT_CARD_NOT_FOUND", "카드 정보를 찾을 수 없습니다."),

    // ===== TRANSACTION (400번대) =====
    TRANSACTION_NOT_FOUND(HttpStatus.OK, "SIM-TRX-401", "TRANSACTION_NOT_FOUND", "거래를 찾을 수 없습니다."),
    TRANSACTION_ALREADY_PROCESSED(HttpStatus.OK, "SIM-TRX-402", "TRANSACTION_ALREADY_PROCESSED", "이미 처리된 거래입니다."),
    TRANSACTION_NOT_CANCELABLE(HttpStatus.OK, "SIM-TRX-403", "TRANSACTION_NOT_CANCELABLE", "취소 불가능한 거래입니다."),
    TRANSACTION_MISMATCH(HttpStatus.OK, "SIM-TRX-404", "TRANSACTION_MISMATCH", "거래 정보가 일치하지 않습니다."),
    TRANSACTION_TOKEN_MISMATCH(HttpStatus.OK, "SIM-TRX-405", "TRANSACTION_TOKEN_MISMATCH", "거래와 토큰이 일치하지 않습니다."),

    // ===== USER (500번대) =====
    USER_BIRTH_INVALID(HttpStatus.OK, "SIM-USER-501", "USER_BIRTH_INVALID", "본인 정보가 일치하지 않습니다."),
    USER_PHONE_INVALID(HttpStatus.OK, "SIM-USER-502", "USER_PHONE_INVALID", "본인 정보가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String reason;
    private final String message;

    public int getHttp() {
        return status.value();
    }
}
