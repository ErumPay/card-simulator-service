package com.erumpay.card_simulator_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "simulator_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulatorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "approval_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal approvalRate;

    @Column(name = "delay_ms", nullable = false)
    private Integer delayMs;

    @Column(name = "reject_pattern", length = 200)
    private String rejectPattern;

    @Builder
    private SimulatorConfig(BigDecimal approvalRate, Integer delayMs, String rejectPattern) {
        this.approvalRate = approvalRate;
        this.delayMs = delayMs == null ? 0 : delayMs;
        this.rejectPattern = rejectPattern;
    }
}
