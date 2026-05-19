package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulatorCardRepository extends JpaRepository<SimulatorCard, Long> {

    Optional<SimulatorCard> findByCardCompanyAndCardNumber(CardCompany cardCompany, String cardNumber);
}
