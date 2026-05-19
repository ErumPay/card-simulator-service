package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorCardRepository extends JpaRepository<SimulatorCard, Long> {
}
