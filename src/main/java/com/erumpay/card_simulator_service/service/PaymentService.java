package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.RandomStringGenerator;
import com.erumpay.card_simulator_service.dto.api.request.PaymentApproveRequest;
import com.erumpay.card_simulator_service.dto.api.response.PaymentApproveResponse;
import com.erumpay.card_simulator_service.dto.api.request.PaymentCancelRequest;
import com.erumpay.card_simulator_service.dto.api.response.PaymentCancelResponse;
import com.erumpay.card_simulator_service.dto.api.request.PaymentInquireRequest;
import com.erumpay.card_simulator_service.dto.api.response.PaymentInquireResponse;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken.TokenStatus;
import com.erumpay.card_simulator_service.entity.SimulatorConfig;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.Category;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
import com.erumpay.card_simulator_service.repository.SimulatorCardRepository;
import com.erumpay.card_simulator_service.repository.SimulatorCardTokenRepository;
import com.erumpay.card_simulator_service.repository.SimulatorConfigRepository;
import com.erumpay.card_simulator_service.repository.SimulatorPaymentHistoryRepository;
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
public class PaymentService {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int APPROVAL_NUMBER_LENGTH = 8;
    private static final int APPROVAL_NUMBER_MAX_RETRY = 3;

    private final SimulatorPaymentHistoryRepository paymentRepository;
    private final SimulatorCardTokenRepository tokenRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorConfigRepository configRepository;
    private final ResponseCodeResolver responseCodeResolver;
    private final AesCryptoUtil aesCryptoUtil;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    @Lazy
    private PaymentService self;

    public PaymentApproveResponse approve(String idempotencyKey, PaymentApproveRequest request) {
        // 트랜잭션 종료 후 지연 적용 (트랜잭션 안에서 sleep을 하면 DB 커넥션이 그 시간만큼 점유됨)
        SimulatorConfig config = loadConfig();
        PaymentApproveResponse response = self.approveInTransaction(idempotencyKey, request);
        applyDelay(config);
        return response;
    }

    @Transactional
    public PaymentApproveResponse approveInTransaction(String idempotencyKey, PaymentApproveRequest request) {
        // 1. 멱등성 검사
        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toApproveResponse(existing.get());
        }

        // 2. 토큰 검증
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        if (token == null) {
            return approveRejectedWithoutRow(idempotencyKey, request);
        }

        // 3. 카드 상태 검증
        SimulatorCard card = cardRepository.findById(token.getCardId()).orElse(null);
        if (card == null || card.getCardStatus() != SimulatorCard.CardStatus.ACTIVE) {
            return approveRejectedWithoutRow(idempotencyKey, request);
        }

        // 4. 시뮬레이션 적용 (지연은 트랜잭션 종료 후 wrapper에서)
        SimulatorConfig config = loadConfig();
        boolean approved = simulate(config, card);

        // 5. 승인번호 생성 + row INSERT
        ResponseType resultType = approved ? ResponseType.SUCCESS : ResponseType.PAYMENT_REJECTED;
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.PAYMENT, resultType);
        LocalDateTime performanceDate = LocalDateTime.now();
        SimulatorPaymentHistory saved = insertWithUniqueApprovalNumber(idempotencyKey,
                () -> SimulatorPaymentHistory.builder()
                        .cardId(card.getCardId())
                        .cardCompany(card.getCardCompany())
                        .pgId(request.pgId())
                        .pgTxnId(request.pgTxnId())
                        .idempotencyKey(idempotencyKey)
                        .paymentStatus(approved ? PaymentStatus.APPROVED : PaymentStatus.FAILED)
                        .originalAmount(request.originalAmount())
                        .approvedAmount(request.approvedAmount())
                        .performanceDate(performanceDate)
                        .responseCode(rc.getResponseCode())
                        .responseMessage(rc.getResponseMessage()));

        return toApproveResponse(saved);
    }

    @Transactional
    public PaymentCancelResponse cancel(String idempotencyKey, PaymentCancelRequest request) {
        // 1. 멱등성 검사 (이번 취소건)
        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toCancelResponse(existing.get());
        }

        // 2. 원거래 조회 (APPROVED 필수)
        var originOpt = paymentRepository
                .findByOriginIdempotencyKeyAndPaymentStatus(request.originIdempotencyKey(), PaymentStatus.APPROVED);
        if (originOpt.isEmpty()) {
            originOpt = paymentRepository.findByIdempotencyKey(request.originIdempotencyKey())
                    .filter(p -> p.getPaymentStatus() == PaymentStatus.APPROVED);
        }
        if (originOpt.isEmpty()) {
            return cancelFailureResponse(idempotencyKey, request, ResponseType.TRANSACTION_NOT_FOUND);
        }
        SimulatorPaymentHistory origin = originOpt.get();

        // 3. 원거래 일관성 검증 (PG 거래 ID, 승인번호)
        if (!origin.getPgTxnId().equals(request.originPgTxnId())
                || !origin.getApprovalNumber().equals(request.approvalNumber())) {
            return cancelFailureResponse(idempotencyKey, request, ResponseType.TRANSACTION_NOT_FOUND);
        }

        // 4. 토큰 일치 검증 (card_company + card_token)
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        if (token == null || !token.getCardId().equals(origin.getCardId())) {
            return cancelFailureResponse(idempotencyKey, request, ResponseType.TRANSACTION_NOT_FOUND);
        }

        // 5. 취소 승인번호 생성 + 취소 row INSERT (origin_pg_txn_id는 원거래 값으로 저장)
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.PAYMENT, ResponseType.SUCCESS);
        SimulatorPaymentHistory canceled = insertWithUniqueApprovalNumber(idempotencyKey,
                () -> SimulatorPaymentHistory.builder()
                        .cardId(origin.getCardId())
                        .cardCompany(origin.getCardCompany())
                        .pgId(origin.getPgId())
                        .pgTxnId(request.pgTxnId())
                        .originPgTxnId(origin.getPgTxnId())
                        .idempotencyKey(idempotencyKey)
                        .originIdempotencyKey(request.originIdempotencyKey())
                        .paymentStatus(PaymentStatus.CANCELED)
                        .originalAmount(origin.getOriginalAmount())
                        .approvedAmount(origin.getApprovedAmount())
                        .performanceDate(origin.getPerformanceDate())
                        .responseCode(rc.getResponseCode())
                        .responseMessage(rc.getResponseMessage()));

        return toCancelResponse(canceled);
    }

    @Transactional(readOnly = true)
    public PaymentInquireResponse inquire(PaymentInquireRequest request) {
        // 카드사는 실제로 서로 독립된 시스템이므로, 본인의 PG/카드사 영역 외 거래는 조회 불가
        var found = paymentRepository.findByPgIdAndCardCompanyAndIdempotencyKey(
                request.pgId(), request.cardCompany(), request.targetIdempotencyKey());
        if (found.isEmpty()) {
            SimulatorResponseCode rc = responseCodeResolver.resolve(Category.TRANSACTION, ResponseType.TRANSACTION_NOT_FOUND);
            return PaymentInquireResponse.builder()
                    .pgId(request.pgId())
                    .idempotencyKey(request.targetIdempotencyKey())
                    .responseCode(rc.getResponseCode())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        }
        SimulatorPaymentHistory row = found.get();
        return PaymentInquireResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .paymentStatus(row.getPaymentStatus())
                .approvalNumber(row.getApprovalNumber())
                .approvedAt(format(row.getCreatedAt()))
                .approvedAmount(row.getApprovedAmount())
                .responseCode(row.getResponseCode())
                .responseMessage(row.getResponseMessage())
                .build();
    }

    private SimulatorCardToken findActiveToken(CardCompany cardCompany, String plainCardToken) {
        // 토큰 평문은 PG에 발급 시 전달되고, DB에는 ECB 암호화 저장. ECB는 결정적이라 동일 평문→동일 암호문이라 매칭 가능.
        String encryptedToken = aesCryptoUtil.encrypt(plainCardToken);
        return tokenRepository
                .findByCardCompanyAndCardTokenAndTokenStatus(cardCompany, encryptedToken, TokenStatus.ACTIVE)
                .orElse(null);
    }

    private SimulatorPaymentHistory insertWithUniqueApprovalNumber(
            String idempotencyKey,
            Supplier<SimulatorPaymentHistory.SimulatorPaymentHistoryBuilder> builderSupplier) {
        for (int attempt = 0; attempt < APPROVAL_NUMBER_MAX_RETRY; attempt++) {
            try {
                return paymentRepository.save(builderSupplier.get()
                        .approvalNumber(RandomStringGenerator.generateHex(APPROVAL_NUMBER_LENGTH))
                        .build());
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 동일 idempotency_key가 먼저 INSERT된 경우 → 기존 row를 echo (멱등성 보장)
                var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    return existing.get();
                }
                // idempotency_key는 비어있음 → approval_number 충돌로 간주, 다음 시도에서 새 번호로 재발급
                if (attempt == APPROVAL_NUMBER_MAX_RETRY - 1) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("approval_number 생성 재시도 실패");
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

    private PaymentApproveResponse approveRejectedWithoutRow(String idempotencyKey, PaymentApproveRequest request) {
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.PAYMENT, ResponseType.PAYMENT_REJECTED);
        return PaymentApproveResponse.builder()
                .pgId(request.pgId())
                .idempotencyKey(idempotencyKey)
                .pgTxnId(request.pgTxnId())
                .paymentStatus(PaymentStatus.FAILED)
                .approvedAmount(request.approvedAmount())
                .responseCode(rc.getResponseCode())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private PaymentCancelResponse cancelFailureResponse(String idempotencyKey, PaymentCancelRequest request,
                                                        ResponseType responseType) {
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.TRANSACTION, responseType);
        return PaymentCancelResponse.builder()
                .pgId(request.pgId())
                .idempotencyKey(idempotencyKey)
                .pgTxnId(request.pgTxnId())
                .approvalNumber(request.approvalNumber())
                .responseCode(rc.getResponseCode())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private PaymentApproveResponse toApproveResponse(SimulatorPaymentHistory row) {
        return PaymentApproveResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .paymentStatus(row.getPaymentStatus())
                .approvalNumber(row.getApprovalNumber())
                .approvedAt(format(row.getCreatedAt()))
                .approvedAmount(row.getApprovedAmount())
                .responseCode(row.getResponseCode())
                .responseMessage(row.getResponseMessage())
                .build();
    }

    private PaymentCancelResponse toCancelResponse(SimulatorPaymentHistory row) {
        return PaymentCancelResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .paymentStatus(row.getPaymentStatus())
                .approvalNumber(row.getApprovalNumber())
                .cancelledAt(format(row.getCreatedAt()))
                .cancelledAmount(row.getApprovedAmount())
                .responseCode(row.getResponseCode())
                .responseMessage(row.getResponseMessage())
                .build();
    }

    private String format(LocalDateTime ts) {
        return ts == null ? null : ts.format(TS_FORMATTER);
    }
}
