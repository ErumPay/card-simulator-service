package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.PasswordHashUtil;
import com.erumpay.card_simulator_service.common.RandomStringGenerator;
import com.erumpay.card_simulator_service.dto.TokenInquireRequest;
import com.erumpay.card_simulator_service.dto.TokenIssueRequest;
import com.erumpay.card_simulator_service.dto.TokenResponse;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken.TokenStatus;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.Category;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
import com.erumpay.card_simulator_service.entity.SimulatorUser;
import com.erumpay.card_simulator_service.repository.SimulatorCardRepository;
import com.erumpay.card_simulator_service.repository.SimulatorCardTokenRepository;
import com.erumpay.card_simulator_service.repository.SimulatorUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CardTokenService {

    private final SimulatorCardTokenRepository tokenRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorUserRepository userRepository;
    private final ResponseCodeResolver responseCodeResolver;
    private final AesCryptoUtil aesCryptoUtil;

    @Transactional
    public TokenResponse issue(String idempotencyKey, TokenIssueRequest request) {
        // 1. 멱등성 검사 — row 있으면 echo, 없으면 신규 발급
        var existing = tokenRepository.findByIssueIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            SimulatorCardToken token = existing.get();
            return toResponse(token, idempotencyKey, cardOf(token.getCardId()));
        }

        // 2. 카드 조회
        SimulatorCard card = cardRepository
                .findByCardCompanyAndCardNumber(request.cardCompany(), aesCryptoUtil.encrypt(request.cardNumber()))
                .orElse(null);
        if (card == null) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    Category.CARD, ResponseType.CARD_INVALID_INFO);
        }

        // 3. 카드 검증 (실패 시 응답만, DB 변경 없음)
        if (card.getCardStatus() != SimulatorCard.CardStatus.ACTIVE) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    Category.CARD, ResponseType.CARD_INVALID_INFO);
        }
        if (!card.getExpiryDate().equals(aesCryptoUtil.encrypt(request.expiryDate()))
                || !card.getCvc().equals(aesCryptoUtil.encrypt(request.cvc()))) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    Category.CARD, ResponseType.CARD_INVALID_INFO);
        }
        if (!PasswordHashUtil.verify(request.password2digit(), card.getCardSalt(), card.getPassword2digit())) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    Category.CARD, ResponseType.CARD_INVALID_PASSWORD);
        }
        SimulatorUser user = userRepository.findById(card.getUserId()).orElse(null);
        if (user == null || !user.getBirthDate().equals(aesCryptoUtil.encrypt(request.birthDate()))) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    Category.USER, ResponseType.USER_INVALID_INFO);
        }

        // 4. (card_id, pg_id) ACTIVE 토큰 중복 검사
        if (tokenRepository.existsByCardIdAndPgIdAndTokenStatus(card.getCardId(), request.pgId(), TokenStatus.ACTIVE)) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    Category.TOKEN, ResponseType.TOKEN_DUPLICATE);
        }

        // 5. 토큰 생성 + ACTIVE row INSERT (동시성: DB UNIQUE 위반 catch → TOKEN_DUPLICATE)
        String plainToken = RandomStringGenerator.generateUuidV4NoHyphen();
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.TOKEN, ResponseType.SUCCESS);
        try {
            SimulatorCardToken saved = tokenRepository.save(SimulatorCardToken.builder()
                    .cardId(card.getCardId())
                    .cardCompany(request.cardCompany())
                    .pgId(request.pgId())
                    .issueIdempotencyKey(idempotencyKey)
                    .cardToken(aesCryptoUtil.encrypt(plainToken))
                    .issueResponseCode(rc.getResponseCode())
                    .issueResponseMessage(rc.getResponseMessage())
                    .tokenStatus(TokenStatus.ACTIVE)
                    .build());

            return TokenResponse.builder()
                    .pgId(request.pgId())
                    .idempotencyKey(idempotencyKey)
                    .tokenStatus(saved.getTokenStatus())
                    .cardToken(plainToken)
                    .cardCompany(request.cardCompany())
                    .maskedNumber(card.getMaskedNumber())
                    .responseCode(rc.getResponseCode())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        } catch (DataIntegrityViolationException e) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    Category.TOKEN, ResponseType.TOKEN_DUPLICATE);
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse inquire(TokenInquireRequest request) {
        var found = tokenRepository.findByIssueIdempotencyKey(request.targetIdempotencyKey());
        if (found.isEmpty()) {
            SimulatorResponseCode rc = responseCodeResolver.resolve(Category.TOKEN, ResponseType.TOKEN_NOT_FOUND);
            return TokenResponse.builder()
                    .idempotencyKey(request.targetIdempotencyKey())
                    .responseCode(rc.getResponseCode())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        }
        SimulatorCardToken token = found.get();
        SimulatorCard card = cardOf(token.getCardId());
        return toResponse(token, request.targetIdempotencyKey(), card);
    }

    private TokenResponse toResponse(SimulatorCardToken token, String idempotencyKey, SimulatorCard card) {
        return TokenResponse.builder()
                .pgId(token.getPgId())
                .idempotencyKey(idempotencyKey)
                .tokenStatus(token.getTokenStatus())
                .cardToken(token.getCardToken() == null ? null : aesCryptoUtil.decrypt(token.getCardToken()))
                .cardCompany(token.getCardCompany())
                .maskedNumber(card == null ? null : card.getMaskedNumber())
                .responseCode(token.getIssueResponseCode())
                .responseMessage(token.getIssueResponseMessage())
                .build();
    }

    private SimulatorCard cardOf(Long cardId) {
        return cardRepository.findById(cardId).orElse(null);
    }

    private TokenResponse failureResponse(String pgId, String idempotencyKey, CardCompany cardCompany,
                                           Category category, ResponseType type) {
        SimulatorResponseCode rc = responseCodeResolver.resolve(category, type);
        return TokenResponse.builder()
                .pgId(pgId)
                .idempotencyKey(idempotencyKey)
                .cardCompany(cardCompany)
                .responseCode(rc.getResponseCode())
                .responseMessage(rc.getResponseMessage())
                .build();
    }
}
