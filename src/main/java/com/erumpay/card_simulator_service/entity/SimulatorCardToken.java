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
@Table(name = "simulator_card_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulatorCardToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "card_token")
    private String cardToken;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", nullable = false, length = 50)
    private CardCompany cardCompany;

    @Column(name = "pg_id", nullable = false, length = 20)
    private String pgId;

    @Column(name = "issue_idempotency_key", nullable = false, length = 64)
    private String issueIdempotencyKey;

    @Column(name = "delete_idempotency_key", length = 64)
    private String deleteIdempotencyKey;

    @Column(name = "issue_response_code", nullable = false, length = 20)
    private String issueResponseCode;

    @Column(name = "issue_response_message", nullable = false)
    private String issueResponseMessage;

    @Column(name = "delete_response_code", length = 20)
    private String deleteResponseCode;

    @Column(name = "delete_response_message")
    private String deleteResponseMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_status", nullable = false)
    private TokenStatus tokenStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TokenStatus {
        ACTIVE, DELETED
    }

    @Builder
    private SimulatorCardToken(Long cardId, CardCompany cardCompany, String pgId, String issueIdempotencyKey,
                                String cardToken, String issueResponseCode, String issueResponseMessage,
                                TokenStatus tokenStatus) {
        this.cardId = cardId;
        this.cardCompany = cardCompany;
        this.pgId = pgId;
        this.issueIdempotencyKey = issueIdempotencyKey;
        this.cardToken = cardToken;
        this.issueResponseCode = issueResponseCode;
        this.issueResponseMessage = issueResponseMessage;
        this.tokenStatus = tokenStatus;
    }

    public void markDeleted(String deleteIdempotencyKey, String responseCode, String responseMessage) {
        if (this.tokenStatus != TokenStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE tokens can be deleted. current=" + this.tokenStatus);
        }
        this.tokenStatus = TokenStatus.DELETED;
        this.deleteIdempotencyKey = deleteIdempotencyKey;
        this.deleteResponseCode = responseCode;
        this.deleteResponseMessage = responseMessage;
    }
}
