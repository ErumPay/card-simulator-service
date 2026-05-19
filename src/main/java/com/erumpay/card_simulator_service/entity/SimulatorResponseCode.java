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

    @Column(name = "response_code", nullable = false, length = 20)
    private String responseCode;

    @Column(name = "response_message", nullable = false)
    private String responseMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false)
    private ResponseType responseType;

    public enum Category {
        TOKEN, CARD, PAYMENT, TRANSACTION, USER, SYSTEM
    }

    public enum ResponseType {
        SUCCESS,
        CARD_LOST, CARD_EXPIRED, CARD_DELETED,
        CARD_INVALID_INFO, CARD_INVALID_PASSWORD,
        TOKEN_NOT_FOUND, TOKEN_DUPLICATE,
        PAYMENT_LIMIT_EXCEEDED, PAYMENT_INSUFFICIENT_BALANCE, PAYMENT_REJECTED,
        TRANSACTION_NOT_FOUND, TRANSACTION_ALREADY_PROCESSED,
        USER_NOT_FOUND, USER_INVALID_INFO,
        SYSTEM_ERROR
    }

    @Builder
    private SimulatorResponseCode(Category category, String responseCode,
                                  String responseMessage, ResponseType responseType) {
        this.category = category;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.responseType = responseType;
    }
}
