package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulatorPaymentHistoryRepository extends JpaRepository<SimulatorPaymentHistory, Long> {

    Optional<SimulatorPaymentHistory> findByIdempotencyKey(String idempotencyKey);

    Optional<SimulatorPaymentHistory> findByOriginIdempotencyKeyAndPaymentStatus(
            String originIdempotencyKey, PaymentStatus paymentStatus);
}
