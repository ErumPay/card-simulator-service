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
@Table(name = "simulator_card_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulatorCardProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "card_company", nullable = false, length = 50)
    private CardCompany cardCompany;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private SimulatorCardProduct(CardCompany cardCompany, String productName) {
        this.cardCompany = cardCompany;
        this.productName = productName;
    }
}
