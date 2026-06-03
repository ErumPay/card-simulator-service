package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.RandomStringGenerator;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalCancelRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalCancelResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalInquireRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalInquireResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalResponse;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken.TokenStatus;
import com.erumpay.card_simulator_service.entity.SimulatorConfig;
import com.erumpay.card_simulator_service.entity.SimulatorPreApproval;
import com.erumpay.card_simulator_service.entity.SimulatorPreApproval.PreApprovalStatus;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.Category;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
import com.erumpay.card_simulator_service.repository.SimulatorCardRepository;
import com.erumpay.card_simulator_service.repository.SimulatorCardTokenRepository;
import com.erumpay.card_simulator_service.repository.SimulatorConfigRepository;
import com.erumpay.card_simulator_service.repository.SimulatorPreApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
@RequiredArgsConstructor
public class PreApprovalService {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int APPROVAL_NUMBER_LENGTH = 8;
    private static final int APPROVAL_NUMBER_MAX_RETRY = 3;

    private final SimulatorPreApprovalRepository preApprovalRepository;
    private final SimulatorCardTokenRepository tokenRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorConfigRepository configRepository;
    private final ResponseCodeResolver responseCodeResolver;
    private final SimulateRejectReasonResolver simulateRejectReasonResolver;
    private final AesCryptoUtil aesCryptoUtil;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    @Lazy
    private PreApprovalService self;

    public PreApprovalResponse request(String idempotencyKey, PreApprovalRequest request) {
        // 트랜잭션 종료 후 지연 적용 (트랜잭션 안에서 sleep을 하면 DB 커넥션이 그 시간만큼 점유됨)
        SimulatorConfig config = loadConfig();
        PreApprovalResponse response = self.requestInTransaction(idempotencyKey, request);
        applyDelay(config);
        return response;
    }

    @Transactional
    public PreApprovalResponse requestInTransaction(String idempotencyKey, PreApprovalRequest request) {
        // 1. 멱등성 검사
        var existing = preApprovalRepository.findByAuthorizeIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            SimulatorPreApproval row = existing.get();
            if (row.getPreApprovalStatus() == PreApprovalStatus.AUTHORIZED) {
                return toAuthorizedResponse(row);
            }
            // CANCELED / FAILED → 종결된 키. 새 Idempotency-Key로 재요청 필요
            return failureResponseWithoutRow(idempotencyKey, request,
                    Category.TRANSACTION, ResponseType.TRANSACTION_ALREADY_PROCESSED);
        }

        // 2. 토큰 검증
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        if (token == null) {
            return failureResponseWithoutRow(idempotencyKey, request,
                    Category.PAYMENT, ResponseType.PAYMENT_TOKEN_INVALID);
        }

        // 3. 카드 상태 검증
        SimulatorCard card = cardRepository.findById(token.getCardId()).orElse(null);
        if (card == null) {
            return failureResponseWithoutRow(idempotencyKey, request,
                    Category.PAYMENT, ResponseType.PAYMENT_CARD_NOT_FOUND);
        }
        ResponseType cardStatusFailure = switch (card.getCardStatus()) {
            case ACTIVE -> null;
            case LOST -> ResponseType.PAYMENT_CARD_LOST;
            case EXPIRED -> ResponseType.PAYMENT_CARD_EXPIRED;
            case DELETED -> ResponseType.PAYMENT_CARD_DELETED;
        };
        if (cardStatusFailure != null) {
            return failureResponseWithoutRow(idempotencyKey, request, Category.PAYMENT, cardStatusFailure);
        }

        // 4. 시뮬레이션 적용 (지연은 트랜잭션 종료 후 wrapper에서)
        SimulatorConfig config = loadConfig();
        boolean approved = simulate(config, card);

        // 5. 가승인 번호 + row INSERT
        ResponseType resultType = approved ? ResponseType.SUCCESS : simulateRejectReasonResolver.resolve(card);
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.PAYMENT, resultType);
        SimulatorPreApproval saved = insertWithUniquePreApprovalNumber(idempotencyKey,
                () -> SimulatorPreApproval.builder()
                        .cardId(card.getCardId())
                        .cardCompany(card.getCardCompany())
                        .pgId(request.pgId())
                        .pgTxnId(request.pgTxnId())
                        .authorizeIdempotencyKey(idempotencyKey)
                        .originalAmount(request.originalAmount())
                        .approvedAmount(request.approvedAmount())
                        .preApprovalStatus(approved ? PreApprovalStatus.AUTHORIZED : PreApprovalStatus.FAILED)
                        .responseCode(rc.getResponseCode())
                        .responseMessage(rc.getResponseMessage()));

        return toAuthorizedResponse(saved);
    }

    @Transactional
    public PreApprovalCancelResponse cancel(String idempotencyKey, PreApprovalCancelRequest request) {
        // 1. 멱등성 검사 (이번 취소건)
        var existingCancel = preApprovalRepository.findByCancelIdempotencyKey(idempotencyKey);
        if (existingCancel.isPresent()) {
            return toCancelResponse(existingCancel.get(), request.pgTxnId());
        }

        // 2. 원 가승인 조회
        var originOpt = preApprovalRepository.findByAuthorizeIdempotencyKey(request.originIdempotencyKey());
        if (originOpt.isEmpty()) {
            return cancelFailureResponse(idempotencyKey, request, ResponseType.TRANSACTION_NOT_FOUND);
        }
        SimulatorPreApproval origin = originOpt.get();
        if (origin.getPreApprovalStatus() != PreApprovalStatus.AUTHORIZED) {
            return cancelFailureResponse(idempotencyKey, request, ResponseType.TRANSACTION_NOT_FOUND);
        }

        // 3. 원거래 일관성 검증 (PG 거래 ID, 가승인 승인번호)
        if (!origin.getPgTxnId().equals(request.originPgTxnId())
                || !origin.getPreApprovalNumber().equals(request.preApprovalNumber())) {
            return cancelFailureResponse(idempotencyKey, request, ResponseType.TRANSACTION_NOT_FOUND);
        }

        // 4. 토큰 일치 검증
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        if (token == null || !token.getCardId().equals(origin.getCardId())) {
            return cancelFailureResponse(idempotencyKey, request, ResponseType.TRANSACTION_NOT_FOUND);
        }

        // 5. row UPDATE (status=CANCELED, cancel_idempotency_key 저장)
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.PAYMENT, ResponseType.SUCCESS);
        try {
            origin.cancel(idempotencyKey, rc.getResponseCode(), rc.getResponseMessage());
            preApprovalRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 동시 취소 요청으로 cancel_idempotency_key UNIQUE 위반 → 기존 결과 echo
            var again = preApprovalRepository.findByCancelIdempotencyKey(idempotencyKey);
            if (again.isPresent()) {
                return toCancelResponse(again.get(), request.pgTxnId());
            }
            throw e;
        }
        return toCancelResponse(origin, request.pgTxnId());
    }

    @Transactional(readOnly = true)
    public PreApprovalInquireResponse inquire(PreApprovalInquireRequest request) {
        // 카드사는 실제로 서로 독립된 시스템이므로, 본인의 PG/카드사 영역 외 거래는 조회 불가
        var found = preApprovalRepository.findByPgIdAndCardCompanyAndAuthorizeIdempotencyKey(
                request.pgId(), request.cardCompany(), request.targetIdempotencyKey());
        if (found.isEmpty()) {
            SimulatorResponseCode rc = responseCodeResolver.resolve(Category.TRANSACTION, ResponseType.TRANSACTION_NOT_FOUND);
            return PreApprovalInquireResponse.builder()
                    .pgId(request.pgId())
                    .idempotencyKey(request.targetIdempotencyKey())
                    .responseHttp(rc.getResponseHttp())
                    .responseCode(rc.getResponseCode())
                    .responseReason(rc.getResponseReason())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        }
        SimulatorPreApproval row = found.get();
        SimulatorResponseCode rc = responseCodeResolver.resolveByCode(row.getResponseCode());
        return PreApprovalInquireResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getAuthorizeIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .preApprovalStatus(row.getPreApprovalStatus())
                .preApprovalId(row.getPreApprovalId())
                .preApprovalNumber(row.getPreApprovalNumber())
                .preApprovedAt(format(row.getCreatedAt()))
                .approvedAmount(row.getApprovedAmount())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private SimulatorCardToken findActiveToken(CardCompany cardCompany, String plainCardToken) {
        String encryptedToken = aesCryptoUtil.encrypt(plainCardToken);
        return tokenRepository
                .findByCardCompanyAndCardTokenAndTokenStatus(cardCompany, encryptedToken, TokenStatus.ACTIVE)
                .orElse(null);
    }

    private SimulatorPreApproval insertWithUniquePreApprovalNumber(
            String idempotencyKey,
            Supplier<SimulatorPreApproval.SimulatorPreApprovalBuilder> builderSupplier) {
        for (int attempt = 0; attempt < APPROVAL_NUMBER_MAX_RETRY; attempt++) {
            try {
                return preApprovalRepository.save(builderSupplier.get()
                        .preApprovalNumber(RandomStringGenerator.generateHex(APPROVAL_NUMBER_LENGTH))
                        .build());
            } catch (DataIntegrityViolationException e) {
                var existing = preApprovalRepository.findByAuthorizeIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    return existing.get();
                }
                if (attempt == APPROVAL_NUMBER_MAX_RETRY - 1) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("pre_approval_number 생성 재시도 실패");
    }

    private SimulatorConfig loadConfig() {
        return configRepository.findAll().stream().findFirst().orElse(null);
    }

    private void applyDelay(SimulatorConfig config) {
        if (config == null || config.getDelayMs() == null || config.getDelayMs() <= 0) return;
        try {
            Thread.sleep(config.getDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean simulate(SimulatorConfig config, SimulatorCard card) {
        if (config == null) return true;
        String pattern = config.getRejectPattern();
        if (pattern != null && !pattern.isBlank()) {
            try {
                String plainCardNumber = aesCryptoUtil.decrypt(card.getCardNumber());
                if (Pattern.compile(pattern).matcher(plainCardNumber).matches()) {
                    return false;
                }
            } catch (PatternSyntaxException ignore) {
                // 잘못된 정규식은 거절 적용하지 않음
            }
        }
        BigDecimal approvalRate = config.getApprovalRate();
        if (approvalRate == null) return true;
        double rate = approvalRate.doubleValue();
        if (rate >= 100.0) return true;
        if (rate <= 0.0) return false;
        return random.nextDouble() * 100.0 < rate;
    }

    private PreApprovalResponse failureResponseWithoutRow(String idempotencyKey, PreApprovalRequest request,
                                                          Category category, ResponseType responseType) {
        SimulatorResponseCode rc = responseCodeResolver.resolve(category, responseType);
        return PreApprovalResponse.builder()
                .pgId(request.pgId())
                .idempotencyKey(idempotencyKey)
                .pgTxnId(request.pgTxnId())
                .preApprovalStatus(PreApprovalStatus.FAILED)
                .approvedAmount(request.approvedAmount())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private PreApprovalCancelResponse cancelFailureResponse(String idempotencyKey, PreApprovalCancelRequest request,
                                                            ResponseType responseType) {
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.TRANSACTION, responseType);
        return PreApprovalCancelResponse.builder()
                .pgId(request.pgId())
                .idempotencyKey(idempotencyKey)
                .pgTxnId(request.pgTxnId())
                .preApprovalNumber(request.preApprovalNumber())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private PreApprovalResponse toAuthorizedResponse(SimulatorPreApproval row) {
        SimulatorResponseCode rc = responseCodeResolver.resolveByCode(row.getResponseCode());
        return PreApprovalResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getAuthorizeIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .preApprovalStatus(row.getPreApprovalStatus())
                .preApprovalId(row.getPreApprovalId())
                .preApprovalNumber(row.getPreApprovalNumber())
                .preApprovedAt(format(row.getCreatedAt()))
                .approvedAmount(row.getApprovedAmount())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private PreApprovalCancelResponse toCancelResponse(SimulatorPreApproval row, Long cancelPgTxnId) {
        SimulatorResponseCode rc = responseCodeResolver.resolveByCode(row.getResponseCode());
        return PreApprovalCancelResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getCancelIdempotencyKey())
                .pgTxnId(cancelPgTxnId)
                .preApprovalStatus(row.getPreApprovalStatus())
                .preApprovalNumber(row.getPreApprovalNumber())
                .cancelledAt(format(row.getUpdatedAt()))
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private String format(LocalDateTime ts) {
        return ts == null ? null : ts.format(TS_FORMATTER);
    }
}
