package com.erumpay.card_simulator_service.entity;

import com.erumpay.card_simulator_service.common.CardCompany;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "simulator_payment_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulatorPaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", nullable = false, length = 50)
    private CardCompany cardCompany;

    @Column(name = "pg_id", nullable = false, length = 20)
    private String pgId;

    @Column(name = "pg_txn_id", nullable = false)
    private Long pgTxnId;

    @Column(name = "origin_pg_txn_id")
    private Long originPgTxnId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "origin_idempotency_key", length = 64)
    private String originIdempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "approved_amount", nullable = false)
    private Long approvedAmount;

    @Column(name = "performance_date", nullable = false)
    private LocalDateTime performanceDate;

    @Column(name = "approval_number", nullable = false, length = 50)
    private String approvalNumber;

    @Column(name = "response_code", nullable = false, length = 20)
    private String responseCode;

    @Column(name = "response_message", nullable = false)
    private String responseMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum PaymentStatus {
        APPROVED, CANCELED, FAILED
    }

    @Builder
    private SimulatorPaymentHistory(Long cardId, CardCompany cardCompany, String pgId, Long pgTxnId,
                                    Long originPgTxnId, String idempotencyKey, String originIdempotencyKey,
                                    PaymentStatus paymentStatus, Long originalAmount, Long approvedAmount,
                                    LocalDateTime performanceDate, String approvalNumber,
                                    String responseCode, String responseMessage) {
        this.cardId = cardId;
        this.cardCompany = cardCompany;
        this.pgId = pgId;
        this.pgTxnId = pgTxnId;
        this.originPgTxnId = originPgTxnId;
        this.idempotencyKey = idempotencyKey;
        this.originIdempotencyKey = originIdempotencyKey;
        this.paymentStatus = paymentStatus;
        this.originalAmount = originalAmount;
        this.approvedAmount = approvedAmount;
        this.performanceDate = performanceDate;
        this.approvalNumber = approvalNumber;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }
}
