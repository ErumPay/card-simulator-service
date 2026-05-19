package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorResponseCodeRepository extends JpaRepository<SimulatorResponseCode, Long> {
}
