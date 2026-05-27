package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.entity.SimulatorCardProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulatorCardProductRepository extends JpaRepository<SimulatorCardProduct, Long> {

    Optional<SimulatorCardProduct> findByCardCompanyAndProductName(CardCompany cardCompany, String productName);
}
