package com.erumpay.card_simulator_service.repository;

import com.erumpay.card_simulator_service.entity.SimulatorCardToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulatorCardTokenRepository extends JpaRepository<SimulatorCardToken, Long> {

    Optional<SimulatorCardToken> findByIssueIdempotencyKey(String issueIdempotencyKey);

    Optional<SimulatorCardToken> findByDeleteIdempotencyKey(String deleteIdempotencyKey);

    boolean existsByCardIdAndPgIdAndTokenStatus(Long cardId, String pgId, SimulatorCardToken.TokenStatus tokenStatus);

    Optional<SimulatorCardToken> findByCardCompanyAndCardTokenAndTokenStatus(
            com.erumpay.card_simulator_service.common.CardCompany cardCompany,
            String cardToken,
            SimulatorCardToken.TokenStatus tokenStatus);
}
