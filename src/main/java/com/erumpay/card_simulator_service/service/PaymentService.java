package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.RandomStringGenerator;
import com.erumpay.card_simulator_service.common.SimResponseCode;
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
    private final SimulateRejectReasonResolver simulateRejectReasonResolver;
    private final AesCryptoUtil aesCryptoUtil;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    @Lazy
    private PaymentService self;

    // [be] 하지혁 260603 Payment API 1 : 결제 승인
    public PaymentApproveResponse approve(String idempotencyKey, PaymentApproveRequest request) {
        // 트랜잭션 안에서 sleep 하면 DB 커넥션이 그 시간만큼 점유되므로 트랜잭션 종료 후 지연 적용
        SimulatorConfig config = loadConfig();
        PaymentApproveResponse response = self.approveInTransaction(idempotencyKey, request);
        applyDelay(config);
        return response;
    }

    // 결제 승인 트랜잭션 처리 (API 1)
    @Transactional
    public PaymentApproveResponse approveInTransaction(String idempotencyKey, PaymentApproveRequest request) {
        // 1. idempotency-Key 기반 중복 요청 검사
        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        // 중복 시 echo
        if (existing.isPresent()) {
            return toApproveResponse(existing.get());
        }

        // 2. 토큰 검증
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        // 토큰 미존재/비활성 예외처리
        if (token == null) {
            return approveRejectedWithoutRow(idempotencyKey, request, SimResponseCode.PAYMENT_TOKEN_INVALID);
        }

        // 3. 카드 조회 및 상태 검증
        SimulatorCard card = cardRepository.findById(token.getCardId()).orElse(null);
        // 카드 미존재 예외처리
        if (card == null) {
            return approveRejectedWithoutRow(idempotencyKey, request, SimResponseCode.PAYMENT_CARD_NOT_FOUND);
        }
        SimResponseCode cardStatusFailure = switch (card.getCardStatus()) {
            case ACTIVE -> null;
            case LOST -> SimResponseCode.PAYMENT_CARD_LOST;
            case EXPIRED -> SimResponseCode.PAYMENT_CARD_EXPIRED;
            case DELETED -> SimResponseCode.PAYMENT_CARD_DELETED;
        };
        // 카드 상태 예외처리
        if (cardStatusFailure != null) {
            return approveRejectedWithoutRow(idempotencyKey, request, cardStatusFailure);
        }

        // 4. 시뮬레이션 적용 (지연은 트랜잭션 종료 후 wrapper에서)
        SimulatorConfig config = loadConfig();
        boolean approved = simulate(config, card);

        // 5. 승인번호 생성 및 DB 갱신
        SimResponseCode rc = approved ? SimResponseCode.PAYMENT_SUCCESS : simulateRejectReasonResolver.resolve(card);
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

    // [be] 하지혁 260603 Payment API 2 : 결제 취소
    @Transactional
    public PaymentCancelResponse cancel(String idempotencyKey, PaymentCancelRequest request) {
        // 1. idempotency-Key 기반 중복 요청 검사 (취소건)
        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        // 중복 시 echo
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
        // 원거래 미존재 예외처리
        if (originOpt.isEmpty()) {
            return cancelFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }
        SimulatorPaymentHistory origin = originOpt.get();

        // 3. 원거래 일관성 검증 (PG 거래 ID, 승인번호)
        if (!origin.getPgTxnId().equals(request.originPgTxnId())
                || !origin.getApprovalNumber().equals(request.approvalNumber())) {
            return cancelFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 4. 토큰 일치 검증 (card_company + card_token)
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        if (token == null || !token.getCardId().equals(origin.getCardId())) {
            return cancelFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 5. 취소 승인번호 생성 및 취소 row INSERT (origin_pg_txn_id는 원거래 값으로 저장)
        SimResponseCode rc = SimResponseCode.PAYMENT_SUCCESS;
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

    // [be] 하지혁 260603 Payment API 3 : 결제 조회
    @Transactional(readOnly = true)
    public PaymentInquireResponse inquire(PaymentInquireRequest request) {
        // 카드사는 실제로 서로 독립된 시스템이므로, 본인의 PG/카드사 영역 외 거래는 조회 불가
        var found = paymentRepository.findByPgIdAndCardCompanyAndIdempotencyKey(
                request.pgId(), request.cardCompany(), request.targetIdempotencyKey());
        // 미존재 시 예외처리
        if (found.isEmpty()) {
            SimResponseCode rc = SimResponseCode.TRANSACTION_NOT_FOUND;
            return PaymentInquireResponse.builder()
                    .pgId(request.pgId())
                    .idempotencyKey(request.targetIdempotencyKey())
                    .responseHttp(rc.getResponseHttp())
                    .responseCode(rc.getResponseCode())
                    .responseReason(rc.getResponseReason())
                    .responseMessage(rc.getResponseMessage())
                    .build();
        }
        SimulatorPaymentHistory row = found.get();
        SimResponseCode rc = SimResponseCode.ofCode(row.getResponseCode());
        return PaymentInquireResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .paymentStatus(row.getPaymentStatus())
                .approvalNumber(row.getApprovalNumber())
                .approvedAt(format(row.getCreatedAt()))
                .approvedAmount(row.getApprovedAmount())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // ACTIVE 토큰 조회. ECB는 결정적이라 동일 평문→동일 암호문이라 매칭 가능 (API 1,2)
    private SimulatorCardToken findActiveToken(CardCompany cardCompany, String plainCardToken) {
        String encryptedToken = aesCryptoUtil.encrypt(plainCardToken);
        return tokenRepository
                .findByCardCompanyAndCardTokenAndTokenStatus(cardCompany, encryptedToken, TokenStatus.ACTIVE)
                .orElse(null);
    }

    // 고유 승인번호 채번 후 INSERT, 충돌 시 재시도 (API 1,2)
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

    // 시뮬레이션 설정 로드
    private SimulatorConfig loadConfig() {
        return configRepository.findAll().stream().findFirst().orElse(null);
    }

    // 타임아웃 테스트용 응답 지연 적용
    private void applyDelay(SimulatorConfig config) {
        if (config == null || config.getDelayMs() == null || config.getDelayMs() <= 0) return;
        try {
            Thread.sleep(config.getDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 거절 패턴 / 승인률 기반 시뮬레이션 (API 1)
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

    // row INSERT 전 실패 시 에러 응답 (API 1)
    private PaymentApproveResponse approveRejectedWithoutRow(String idempotencyKey, PaymentApproveRequest request,
                                                              SimResponseCode rc) {
        return PaymentApproveResponse.builder()
                .pgId(request.pgId())
                .idempotencyKey(idempotencyKey)
                .pgTxnId(request.pgTxnId())
                .paymentStatus(PaymentStatus.FAILED)
                .approvedAmount(request.approvedAmount())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // 취소 row INSERT 전 실패 시 에러 응답 (API 2)
    private PaymentCancelResponse cancelFailureResponse(String idempotencyKey, PaymentCancelRequest request,
                                                        SimResponseCode rc) {
        return PaymentCancelResponse.builder()
                .pgId(request.pgId())
                .idempotencyKey(idempotencyKey)
                .pgTxnId(request.pgTxnId())
                .approvalNumber(request.approvalNumber())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // row → 승인 응답 매핑 (API 1)
    private PaymentApproveResponse toApproveResponse(SimulatorPaymentHistory row) {
        SimResponseCode rc = SimResponseCode.ofCode(row.getResponseCode());
        return PaymentApproveResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .paymentStatus(row.getPaymentStatus())
                .approvalNumber(row.getApprovalNumber())
                .approvedAt(format(row.getCreatedAt()))
                .approvedAmount(row.getApprovedAmount())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // row → 취소 응답 매핑 (API 2)
    private PaymentCancelResponse toCancelResponse(SimulatorPaymentHistory row) {
        SimResponseCode rc = SimResponseCode.ofCode(row.getResponseCode());
        return PaymentCancelResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .paymentStatus(row.getPaymentStatus())
                .approvalNumber(row.getApprovalNumber())
                .cancelledAt(format(row.getCreatedAt()))
                .cancelledAmount(row.getApprovedAmount())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // LocalDateTime → 응답용 yyyyMMddHHmmss 포맷팅
    private String format(LocalDateTime ts) {
        return ts == null ? null : ts.format(TS_FORMATTER);
    }
}
