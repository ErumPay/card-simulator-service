package com.erumpay.card_simulator_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "simulator_response_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulatorResponseCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code_id")
    private Long codeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "response_http", nullable = false)
    private Integer responseHttp;

    @Column(name = "response_code", nullable = false, length = 20, unique = true)
    private String responseCode;

    @Column(name = "response_reason", nullable = false, length = 50)
    private String responseReason;

    @Column(name = "response_message", nullable = false)
    private String responseMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false)
    private ResponseType responseType;

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

    @Builder
    private SimulatorResponseCode(Category category, Integer responseHttp, String responseCode,
                                  String responseReason, String responseMessage, ResponseType responseType) {
        this.category = category;
        this.responseHttp = responseHttp;
        this.responseCode = responseCode;
        this.responseReason = responseReason;
        this.responseMessage = responseMessage;
        this.responseType = responseType;
    }
}
