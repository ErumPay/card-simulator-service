package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulatorResponseCodeRepository extends JpaRepository<SimulatorResponseCode, Long> {

    Optional<SimulatorResponseCode> findFirstByCategoryAndResponseType(
            SimulatorResponseCode.Category category, SimulatorResponseCode.ResponseType responseType);
}
