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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "simulator_pre_approval")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulatorPreApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pre_approval_id")
    private Long preApprovalId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "card_company", nullable = false, length = 50)
    private CardCompany cardCompany;

    @Column(name = "pg_id", nullable = false, length = 20)
    private String pgId;

    @Column(name = "pg_txn_id", nullable = false)
    private Long pgTxnId;

    @Column(name = "authorize_idempotency_key", nullable = false, length = 64)
    private String authorizeIdempotencyKey;

    @Column(name = "cancel_idempotency_key", length = 64)
    private String cancelIdempotencyKey;

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "approved_amount", nullable = false)
    private Long approvedAmount;

    @Column(name = "pre_approval_number", nullable = false, length = 50)
    private String preApprovalNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_approval_status", nullable = false)
    private PreApprovalStatus preApprovalStatus;

    @Column(name = "response_code", nullable = false, length = 20)
    private String responseCode;

    @Column(name = "response_message", nullable = false)
    private String responseMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum PreApprovalStatus {
        AUTHORIZED, CANCELED, CAPTURED, FAILED
    }

    @Builder
    private SimulatorPreApproval(Long cardId, CardCompany cardCompany, String pgId, Long pgTxnId,
                                 String authorizeIdempotencyKey, Long originalAmount, Long approvedAmount,
                                 String preApprovalNumber, PreApprovalStatus preApprovalStatus,
                                 String responseCode, String responseMessage) {
        this.cardId = cardId;
        this.cardCompany = cardCompany;
        this.pgId = pgId;
        this.pgTxnId = pgTxnId;
        this.authorizeIdempotencyKey = authorizeIdempotencyKey;
        this.originalAmount = originalAmount;
        this.approvedAmount = approvedAmount;
        this.preApprovalNumber = preApprovalNumber;
        this.preApprovalStatus = preApprovalStatus;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    public void cancel(String cancelIdempotencyKey, String responseCode, String responseMessage) {
        if (this.preApprovalStatus != PreApprovalStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Only AUTHORIZED pre-approval can be canceled. current=" + this.preApprovalStatus);
        }
        this.preApprovalStatus = PreApprovalStatus.CANCELED;
        this.cancelIdempotencyKey = cancelIdempotencyKey;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    public void capture(String responseCode, String responseMessage) {
        if (this.preApprovalStatus != PreApprovalStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Only AUTHORIZED pre-approval can be captured. current=" + this.preApprovalStatus);
        }
        this.preApprovalStatus = PreApprovalStatus.CAPTURED;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }
}
