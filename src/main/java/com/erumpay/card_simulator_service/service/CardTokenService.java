package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.PasswordHashUtil;
import com.erumpay.card_simulator_service.common.RandomStringGenerator;
import com.erumpay.card_simulator_service.common.SimResponseCode;
import com.erumpay.card_simulator_service.dto.api.request.TokenDeleteRequest;
import com.erumpay.card_simulator_service.dto.api.response.TokenDeleteResponse;
import com.erumpay.card_simulator_service.dto.api.request.TokenInquireRequest;
import com.erumpay.card_simulator_service.dto.api.request.TokenIssueRequest;
import com.erumpay.card_simulator_service.dto.api.response.TokenResponse;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken.TokenStatus;
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
    private final AesCryptoUtil aesCryptoUtil;

    // [be] 하지혁 260603 CardToken API 1 : 카드사 토큰 발급
    @Transactional
    public TokenResponse issue(String idempotencyKey, TokenIssueRequest request) {
        // 1. idempotency-Key 기반 중복 요청 검사
        var existing = tokenRepository.findByIssueIdempotencyKey(idempotencyKey);

        // 중복 시 예외처리
        if (existing.isPresent()) {
            SimulatorCardToken token = existing.get();
            // DELETED 토큰 echo
            if (token.getTokenStatus() == TokenStatus.DELETED) {
                return toAlreadyDeletedTokenResponse(token, idempotencyKey);
            }
            // ACTIVE 토큰 echo
            return toIssuedTokenResponse(token, idempotencyKey, cardOf(token.getCardId()));
        }

        // 2. 카드 조회
        SimulatorCard card = cardRepository
                .findByCardCompanyAndCardNumber(request.cardCompany(), aesCryptoUtil.encrypt(request.cardNumber()))
                .orElse(null);
        // 미존재 시 예외처리
        if (card == null) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    SimResponseCode.CARD_NOT_FOUND);
        }

        // 3. 카드 검증
        SimResponseCode cardStatusFailure = switch (card.getCardStatus()) {
            case ACTIVE -> null;
            case LOST -> SimResponseCode.CARD_LOST;
            case EXPIRED -> SimResponseCode.CARD_EXPIRED;
            case DELETED -> SimResponseCode.CARD_DELETED;
        };
        // 카드 상태 예외처리
        if (cardStatusFailure != null) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(), cardStatusFailure);
        }
        // 카드 만료일 불일치 예외처리
        if (!card.getExpiryDate().equals(aesCryptoUtil.encrypt(request.expiryDate()))) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    SimResponseCode.CARD_INVALID_EXPIRY);
        }
        // CVC 불일치 예외처리
        if (!card.getCvc().equals(aesCryptoUtil.encrypt(request.cvc()))) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    SimResponseCode.CARD_INVALID_CVC);
        }
        // 비밀번호 앞 두 자리 불일치 예외처리
        if (!PasswordHashUtil.verify(request.password2digit(), card.getCardSalt(), card.getPassword2digit())) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    SimResponseCode.CARD_INVALID_PASSWORD);
        }

        // 4. 사용자 검증
        SimulatorUser user = userRepository.findById(card.getUserId()).orElse(null);
        // 사용자 생년월일 불일치 예외처리
        if (user == null || !user.getBirthDate().equals(aesCryptoUtil.encrypt(request.birthDate()))) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    SimResponseCode.USER_BIRTH_INVALID);
        }

        // 5. 기존 ACTIVE 토큰 존재 시 자동 폐기
        tokenRepository.findByCardIdAndPgIdAndTokenStatus(card.getCardId(), request.pgId(), TokenStatus.ACTIVE)
                .ifPresent(existingActive -> {
                    existingActive.markAutoDeleted();
                    tokenRepository.flush();
                });

        // 6. 토큰 생성 및 DB 갱신
        String plainToken = RandomStringGenerator.generateUuidV4NoHyphen();
        SimResponseCode rc = SimResponseCode.TOKEN_SUCCESS;
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
                    .responseHttp(rc.getResponseHttp())
                    .responseCode(rc.getResponseCode())
                    .responseReason(rc.getResponseReason())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        } catch (DataIntegrityViolationException e) {
            return failureResponse(request.pgId(), idempotencyKey, request.cardCompany(),
                    SimResponseCode.TOKEN_DUPLICATE);
        }
    }

    // [be] 하지혁 260603 CardToken API 2 : 카드사 토큰 삭제
    @Transactional
    public TokenDeleteResponse delete(String idempotencyKey, TokenDeleteRequest request) {
        // 1. idempotency-Key 기반 중복 요청 검사
        var existing = tokenRepository.findByDeleteIdempotencyKey(idempotencyKey);

        // 중복 시 예외처리
        if (existing.isPresent()) {
            return toAlreadyDeletedDeleteResponse(existing.get(), idempotencyKey);
        }

        // 2. 카드사 토큰 조회
        String encryptedToken = aesCryptoUtil.encrypt(request.cardToken());
        SimulatorCardToken token = tokenRepository
                .findByCardCompanyAndCardToken(request.cardCompany(), encryptedToken)
                .orElse(null);
        // 카드사 토큰 미존재 예외처리
        if (token == null) {
            SimResponseCode rc = SimResponseCode.TOKEN_NOT_FOUND;
            return TokenDeleteResponse.builder()
                    .pgId(request.pgId())
                    .idempotencyKey(idempotencyKey)
                    .cardToken(request.cardToken())
                    .responseHttp(rc.getResponseHttp())
                    .responseCode(rc.getResponseCode())
                    .responseReason(rc.getResponseReason())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        }
        // 카드사 토큰 이미 삭제 예외처리
        if (token.getTokenStatus() != TokenStatus.ACTIVE) {
            SimResponseCode rc = SimResponseCode.TOKEN_ALREADY_DELETED;
            return TokenDeleteResponse.builder()
                    .pgId(request.pgId())
                    .idempotencyKey(idempotencyKey)
                    .cardToken(request.cardToken())
                    .responseHttp(rc.getResponseHttp())
                    .responseCode(rc.getResponseCode())
                    .responseReason(rc.getResponseReason())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        }

        // 3. 토큰 삭제 및 DB 갱신
        SimResponseCode rc = SimResponseCode.TOKEN_SUCCESS;
        try {
            token.markDeleted(idempotencyKey, rc.getResponseCode(), rc.getResponseMessage());
            tokenRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 동시 삭제 요청 예외처리
            var again = tokenRepository.findByDeleteIdempotencyKey(idempotencyKey);
            if (again.isPresent()) {
                return toAlreadyDeletedDeleteResponse(again.get(), idempotencyKey);
            }
            throw e;
        }
        return toDeleteResponse(token, idempotencyKey);
    }

    // [be] 하지혁 260603 CardToken API 3 : 카드사 토큰 조회
    @Transactional(readOnly = true)
    public TokenResponse inquire(TokenInquireRequest request) {
        // 1. idempotency-Key 기반 중복 요청 검사
        var found = tokenRepository.findByIssueIdempotencyKey(request.targetIdempotencyKey());
        // 미존재 시 예외처리 (발급 안됨)
        if (found.isEmpty()) {
            SimResponseCode rc = SimResponseCode.TOKEN_ISSUE_NOT_FOUND;
            return TokenResponse.builder()
                    .idempotencyKey(request.targetIdempotencyKey())
                    .responseHttp(rc.getResponseHttp())
                    .responseCode(rc.getResponseCode())
                    .responseReason(rc.getResponseReason())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        }
        SimulatorCardToken token = found.get();
        // DELETED 토큰 echo
        if (token.getTokenStatus() == TokenStatus.DELETED) {
            return toAlreadyDeletedTokenResponse(token, request.targetIdempotencyKey());
        }
        // ACTIVE 토큰 echo
        SimulatorCard card = cardOf(token.getCardId());
        return toIssuedTokenResponse(token, request.targetIdempotencyKey(), card);
    }

    // idempotencyKey 조회 시 ACTIVE 토큰 echo 응답 (API 1,3)
    private TokenResponse toIssuedTokenResponse(SimulatorCardToken token, String idempotencyKey, SimulatorCard card) {
        SimResponseCode rc = SimResponseCode.ofCode(token.getIssueResponseCode());
        return TokenResponse.builder()
                .pgId(token.getPgId())
                .idempotencyKey(idempotencyKey)
                .tokenStatus(token.getTokenStatus())
                .cardToken(aesCryptoUtil.decrypt(token.getCardToken()))
                .cardCompany(token.getCardCompany())
                .maskedNumber(card.getMaskedNumber())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // idempotencyKey 조회 시 DELETED 토큰 응답 (API 1)
    private TokenResponse toAlreadyDeletedTokenResponse(SimulatorCardToken token, String idempotencyKey) {
        SimResponseCode rc = SimResponseCode.TOKEN_ALREADY_DELETED;
        return TokenResponse.builder()
                .pgId(token.getPgId())
                .idempotencyKey(idempotencyKey)
                .tokenStatus(TokenStatus.DELETED)
                .cardToken(null)
                .cardCompany(null)
                .maskedNumber(null)
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // echo 응답을 위한 카드 정보 조회 (API 1,3)
    private SimulatorCard cardOf(Long cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalStateException(
                        "SimulatorCard not found for token reference: cardId=" + cardId));
    }

    // 토큰 삭제 처리 시 정상 완료 응답 (API 2)
    private TokenDeleteResponse toDeleteResponse(SimulatorCardToken token, String idempotencyKey) {
        SimResponseCode rc = SimResponseCode.ofCode(token.getDeleteResponseCode());
        return TokenDeleteResponse.builder()
                .pgId(token.getPgId())
                .idempotencyKey(idempotencyKey)
                .cardToken(aesCryptoUtil.decrypt(token.getCardToken()))
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // delete_idempotency_key 중복 echo 시 ALREADY_DELETED 응답
    private TokenDeleteResponse toAlreadyDeletedDeleteResponse(SimulatorCardToken token, String idempotencyKey) {
        SimResponseCode rc = SimResponseCode.TOKEN_ALREADY_DELETED;
        return TokenDeleteResponse.builder()
                .pgId(token.getPgId())
                .idempotencyKey(idempotencyKey)
                .cardToken(aesCryptoUtil.decrypt(token.getCardToken()))
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // 토큰 관련 로직 실패 시 에러 응답 (API 1)
    private TokenResponse failureResponse(String pgId, String idempotencyKey, CardCompany cardCompany,
                                           SimResponseCode rc) {
        return TokenResponse.builder()
                .pgId(pgId)
                .idempotencyKey(idempotencyKey)
                .cardCompany(cardCompany)
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }
}

