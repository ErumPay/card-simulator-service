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
@Table(name = "simulator_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulatorCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "card_company", nullable = false, length = 50)
    private CardCompany cardCompany;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "masked_number", nullable = false, length = 25)
    private String maskedNumber;

    @Column(name = "expiry_date", nullable = false)
    private String expiryDate;

    @Column(nullable = false)
    private String cvc;

    @Column(name = "password_2digit", nullable = false, length = 64)
    private String password2digit;

    @Column(name = "card_salt", nullable = false, length = 64)
    private String cardSalt;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false)
    private CardStatus cardStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CardStatus {
        ACTIVE, LOST, EXPIRED, DELETED
    }

    @Builder
    private SimulatorCard(Long userId, Long productId, CardCompany cardCompany, String cardNumber,
                          String maskedNumber, String expiryDate, String cvc,
                          String password2digit, String cardSalt, CardStatus cardStatus) {
        this.userId = userId;
        this.productId = productId;
        this.cardCompany = cardCompany;
        this.cardNumber = cardNumber;
        this.maskedNumber = maskedNumber;
        this.expiryDate = expiryDate;
        this.cvc = cvc;
        this.password2digit = password2digit;
        this.cardSalt = cardSalt;
        this.cardStatus = cardStatus == null ? CardStatus.ACTIVE : cardStatus;
    }
}
