package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorCardProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorCardProductRepository extends JpaRepository<SimulatorCardProduct, Long> {
}
