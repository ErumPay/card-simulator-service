package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorUserRepository extends JpaRepository<SimulatorUser, Long> {
}
