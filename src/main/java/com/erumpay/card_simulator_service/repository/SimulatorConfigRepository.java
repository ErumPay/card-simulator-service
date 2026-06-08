package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorConfigRepository extends JpaRepository<SimulatorConfig, Long> {
}
