package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.entity.SimulatorPreApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulatorPreApprovalRepository extends JpaRepository<SimulatorPreApproval, Long> {

    Optional<SimulatorPreApproval> findByAuthorizeIdempotencyKey(String authorizeIdempotencyKey);

    Optional<SimulatorPreApproval> findByCancelIdempotencyKey(String cancelIdempotencyKey);

    Optional<SimulatorPreApproval> findByPgIdAndCardCompanyAndAuthorizeIdempotencyKey(
            String pgId, CardCompany cardCompany, String authorizeIdempotencyKey);
}
