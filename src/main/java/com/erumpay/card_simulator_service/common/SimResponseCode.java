package com.erumpay.card_simulator_service.common;

import com.erumpay.card_simulator_service.exception.CustomException;
import com.erumpay.card_simulator_service.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

// [be] 하지혁 260604 카드 시뮬레이터 응답 코드 enum
@Getter
@AllArgsConstructor
public enum SimResponseCode {

    // TOKEN (명세 5개)
    TOKEN_SUCCESS(Category.TOKEN, ResponseType.SUCCESS, 200, "SIM-TOKEN-100", "TOKEN_SUCCESS", "정상 처리되었습니다."),
    TOKEN_NOT_FOUND(Category.TOKEN, ResponseType.TOKEN_NOT_FOUND, 200, "SIM-TOKEN-101", "TOKEN_NOT_FOUND", "토큰을 찾을 수 없습니다."),
    TOKEN_DUPLICATE(Category.TOKEN, ResponseType.TOKEN_DUPLICATE, 200, "SIM-TOKEN-102", "TOKEN_DUPLICATE", "이미 발급된 토큰이 존재합니다."),
    TOKEN_ALREADY_DELETED(Category.TOKEN, ResponseType.TOKEN_ALREADY_DELETED, 200, "SIM-TOKEN-103", "TOKEN_ALREADY_DELETED", "이미 삭제된 토큰입니다."),
    TOKEN_ISSUE_NOT_FOUND(Category.TOKEN, ResponseType.TOKEN_ISSUE_NOT_FOUND, 200, "SIM-TOKEN-104", "TOKEN_ISSUE_NOT_FOUND", "발급 이력을 찾을 수 없습니다."),

    // CARD (명세 10개)
    CARD_SUCCESS(Category.CARD, ResponseType.SUCCESS, 200, "SIM-CARD-200", "CARD_SUCCESS", "정상 처리되었습니다."),
    CARD_LOST(Category.CARD, ResponseType.CARD_LOST, 200, "SIM-CARD-201", "CARD_LOST", "분실 신고된 카드입니다."),
    CARD_EXPIRED(Category.CARD, ResponseType.CARD_EXPIRED, 200, "SIM-CARD-202", "CARD_EXPIRED", "만료된 카드입니다."),
    CARD_DELETED(Category.CARD, ResponseType.CARD_DELETED, 200, "SIM-CARD-203", "CARD_DELETED", "해지된 카드입니다."),
    CARD_INVALID_PASSWORD(Category.CARD, ResponseType.CARD_INVALID_PASSWORD, 200, "SIM-CARD-205", "CARD_INVALID_PASSWORD", "비밀번호가 일치하지 않습니다."),
    CARD_NOT_FOUND(Category.CARD, ResponseType.CARD_NOT_FOUND, 200, "SIM-CARD-206", "CARD_NOT_FOUND", "존재하지 않는 카드입니다."),
    CARD_INVALID_EXPIRY(Category.CARD, ResponseType.CARD_INVALID_EXPIRY, 200, "SIM-CARD-207", "CARD_INVALID_EXPIRY", "카드 유효기간이 일치하지 않습니다."),
    CARD_INVALID_CVC(Category.CARD, ResponseType.CARD_INVALID_CVC, 200, "SIM-CARD-208", "CARD_INVALID_CVC", "CVC가 일치하지 않습니다."),
    CARD_PRODUCT_NOT_FOUND(Category.CARD, ResponseType.CARD_PRODUCT_NOT_FOUND, 200, "SIM-CARD-209", "CARD_PRODUCT_NOT_FOUND", "카드 상품을 찾을 수 없습니다."),
    CARD_NOT_OWNED(Category.CARD, ResponseType.CARD_NOT_OWNED, 200, "SIM-CARD-210", "CARD_NOT_OWNED", "사용자가 해당 카드를 보유하지 않습니다."),

    // PAYMENT (명세 8개)
    PAYMENT_SUCCESS(Category.PAYMENT, ResponseType.SUCCESS, 200, "SIM-PAYMENT-300", "PAYMENT_SUCCESS", "정상 처리되었습니다."),
    PAYMENT_LIMIT_EXCEEDED(Category.PAYMENT, ResponseType.PAYMENT_LIMIT_EXCEEDED, 200, "SIM-PAYMENT-301", "PAYMENT_LIMIT_EXCEEDED", "한도를 초과했습니다."),
    PAYMENT_INSUFFICIENT_BALANCE(Category.PAYMENT, ResponseType.PAYMENT_INSUFFICIENT_BALANCE, 200, "SIM-PAYMENT-302", "PAYMENT_INSUFFICIENT_BALANCE", "잔액이 부족합니다."),
    PAYMENT_CARD_EXPIRED(Category.PAYMENT, ResponseType.PAYMENT_CARD_EXPIRED, 200, "SIM-PAYMENT-303", "PAYMENT_CARD_EXPIRED", "만료된 카드입니다."),
    PAYMENT_CARD_LOST(Category.PAYMENT, ResponseType.PAYMENT_CARD_LOST, 200, "SIM-PAYMENT-304", "PAYMENT_CARD_LOST", "분실 신고된 카드입니다."),
    PAYMENT_CARD_DELETED(Category.PAYMENT, ResponseType.PAYMENT_CARD_DELETED, 200, "SIM-PAYMENT-305", "PAYMENT_CARD_DELETED", "해지된 카드입니다."),
    PAYMENT_TOKEN_INVALID(Category.PAYMENT, ResponseType.PAYMENT_TOKEN_INVALID, 200, "SIM-PAYMENT-306", "PAYMENT_TOKEN_INVALID", "결제 토큰이 유효하지 않습니다."),
    PAYMENT_CARD_NOT_FOUND(Category.PAYMENT, ResponseType.PAYMENT_CARD_NOT_FOUND, 200, "SIM-PAYMENT-307", "PAYMENT_CARD_NOT_FOUND", "카드 정보를 찾을 수 없습니다."),

    // TRANSACTION (명세 5개)
    TRANSACTION_NOT_FOUND(Category.TRANSACTION, ResponseType.TRANSACTION_NOT_FOUND, 200, "SIM-TRANSACTION-401", "TRANSACTION_NOT_FOUND", "거래를 찾을 수 없습니다."),
    TRANSACTION_ALREADY_PROCESSED(Category.TRANSACTION, ResponseType.TRANSACTION_ALREADY_PROCESSED, 200, "SIM-TRANSACTION-402", "TRANSACTION_ALREADY_PROCESSED", "이미 처리된 거래입니다."),
    TRANSACTION_NOT_CANCELABLE(Category.TRANSACTION, ResponseType.TRANSACTION_NOT_CANCELABLE, 200, "SIM-TRANSACTION-403", "TRANSACTION_NOT_CANCELABLE", "취소 불가능한 거래입니다."),
    TRANSACTION_MISMATCH(Category.TRANSACTION, ResponseType.TRANSACTION_MISMATCH, 200, "SIM-TRANSACTION-404", "TRANSACTION_MISMATCH", "거래 정보가 일치하지 않습니다."),
    TRANSACTION_TOKEN_MISMATCH(Category.TRANSACTION, ResponseType.TRANSACTION_TOKEN_MISMATCH, 200, "SIM-TRANSACTION-405", "TRANSACTION_TOKEN_MISMATCH", "거래와 토큰이 일치하지 않습니다."),

    // USER (명세 2개)
    USER_BIRTH_INVALID(Category.USER, ResponseType.USER_BIRTH_INVALID, 200, "SIM-USER-501", "USER_BIRTH_INVALID", "본인 정보가 일치하지 않습니다."),
    USER_PHONE_INVALID(Category.USER, ResponseType.USER_PHONE_INVALID, 200, "SIM-USER-502", "USER_PHONE_INVALID", "본인 정보가 일치하지 않습니다."),
    ;

    private final Category category;
    private final ResponseType type;
    private final int responseHttp;
    private final String responseCode;
    private final String responseReason;
    private final String responseMessage;

    public static SimResponseCode of(Category category, ResponseType type) {
        for (SimResponseCode r : values()) {
            if (r.category == category && r.type == type) return r;
        }
        throw new CustomException(ErrorCode.RESPONSE_CODE_MAPPING_MISSING);
    }

    public static SimResponseCode ofCode(String code) {
        for (SimResponseCode r : values()) {
            if (r.responseCode.equals(code)) return r;
        }
        throw new CustomException(ErrorCode.RESPONSE_CODE_MAPPING_MISSING);
    }

    public enum Category {
        TOKEN, CARD, PAYMENT, TRANSACTION, USER
    }

    public enum ResponseType {
        SUCCESS,
        CARD_NOT_FOUND,
        CARD_LOST, CARD_EXPIRED, CARD_DELETED,
        CARD_INVALID_EXPIRY, CARD_INVALID_CVC, CARD_INVALID_PASSWORD,
        CARD_PRODUCT_NOT_FOUND, CARD_NOT_OWNED,
        TOKEN_NOT_FOUND, TOKEN_DUPLICATE, TOKEN_ALREADY_DELETED, TOKEN_ISSUE_NOT_FOUND,
        PAYMENT_LIMIT_EXCEEDED, PAYMENT_INSUFFICIENT_BALANCE,
        PAYMENT_CARD_EXPIRED, PAYMENT_CARD_LOST, PAYMENT_CARD_DELETED,
        PAYMENT_TOKEN_INVALID, PAYMENT_CARD_NOT_FOUND,
        TRANSACTION_NOT_FOUND, TRANSACTION_ALREADY_PROCESSED,
        TRANSACTION_NOT_CANCELABLE, TRANSACTION_MISMATCH, TRANSACTION_TOKEN_MISMATCH,
        USER_BIRTH_INVALID, USER_PHONE_INVALID
    }
}
