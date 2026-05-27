package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SimulatorPaymentHistoryRepository extends JpaRepository<SimulatorPaymentHistory, Long> {

    Optional<SimulatorPaymentHistory> findByIdempotencyKey(String idempotencyKey);

    Optional<SimulatorPaymentHistory> findByOriginIdempotencyKeyAndPaymentStatus(
            String originIdempotencyKey, PaymentStatus paymentStatus);

    Optional<SimulatorPaymentHistory> findByPgIdAndCardCompanyAndIdempotencyKey(
            String pgId, CardCompany cardCompany, String idempotencyKey);

    @Query("""
            SELECT COALESCE(SUM(p.approvedAmount), 0)
            FROM SimulatorPaymentHistory p
            WHERE p.cardId = :cardId
              AND p.paymentStatus = :status
              AND p.performanceDate >= :start
              AND p.performanceDate < :end
            """)
    long sumApprovedAmount(@Param("cardId") Long cardId,
                           @Param("status") PaymentStatus status,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);
}
