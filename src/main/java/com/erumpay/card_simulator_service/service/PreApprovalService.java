package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.RandomStringGenerator;
import com.erumpay.card_simulator_service.common.SimResponseCode;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalCancelRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalCancelResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalCaptureRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalCaptureResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalInquireRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalInquireResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalResponse;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken.TokenStatus;
import com.erumpay.card_simulator_service.entity.SimulatorConfig;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import com.erumpay.card_simulator_service.entity.SimulatorPreApproval;
import com.erumpay.card_simulator_service.entity.SimulatorPreApproval.PreApprovalStatus;
import com.erumpay.card_simulator_service.repository.SimulatorCardRepository;
import com.erumpay.card_simulator_service.repository.SimulatorCardTokenRepository;
import com.erumpay.card_simulator_service.repository.SimulatorConfigRepository;
import com.erumpay.card_simulator_service.repository.SimulatorPaymentHistoryRepository;
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
    private final SimulatorPaymentHistoryRepository paymentHistoryRepository;
    private final SimulatorCardTokenRepository tokenRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorConfigRepository configRepository;
    private final SimulateRejectReasonResolver simulateRejectReasonResolver;
    private final AesCryptoUtil aesCryptoUtil;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    @Lazy
    private PreApprovalService self;

    // [be] 하지혁 260603 PreApproval API 1 : 가승인 요청
    public PreApprovalResponse request(String idempotencyKey, PreApprovalRequest request) {
        // 트랜잭션 안에서 sleep 하면 DB 커넥션이 그 시간만큼 점유되므로 트랜잭션 종료 후 지연 적용
        SimulatorConfig config = loadConfig();
        PreApprovalResponse response = self.requestInTransaction(idempotencyKey, request);
        applyDelay(config);
        return response;
    }

    // 가승인 트랜잭션 처리 (API 1)
    @Transactional
    public PreApprovalResponse requestInTransaction(String idempotencyKey, PreApprovalRequest request) {
        // 1. idempotency-Key 기반 중복 요청 검사
        var existing = preApprovalRepository.findByAuthorizeIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            SimulatorPreApproval row = existing.get();
            // AUTHORIZED 가승인 echo
            if (row.getPreApprovalStatus() == PreApprovalStatus.AUTHORIZED) {
                return toAuthorizedResponse(row);
            }
            // CANCELED / FAILED → 종결된 키. 새 Idempotency-Key로 재요청 필요
            return failureResponseWithoutRow(idempotencyKey, request,
                    SimResponseCode.TRANSACTION_ALREADY_PROCESSED);
        }

        // 2. 토큰 검증
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        // 토큰 미존재/비활성 예외처리
        if (token == null) {
            return failureResponseWithoutRow(idempotencyKey, request, SimResponseCode.PAYMENT_TOKEN_INVALID);
        }

        // 3. 카드 조회 및 상태 검증
        SimulatorCard card = cardRepository.findById(token.getCardId()).orElse(null);
        // 카드 미존재 예외처리
        if (card == null) {
            return failureResponseWithoutRow(idempotencyKey, request, SimResponseCode.PAYMENT_CARD_NOT_FOUND);
        }
        SimResponseCode cardStatusFailure = switch (card.getCardStatus()) {
            case ACTIVE -> null;
            case LOST -> SimResponseCode.PAYMENT_CARD_LOST;
            case EXPIRED -> SimResponseCode.PAYMENT_CARD_EXPIRED;
            case DELETED -> SimResponseCode.PAYMENT_CARD_DELETED;
        };
        // 카드 상태 예외처리
        if (cardStatusFailure != null) {
            return failureResponseWithoutRow(idempotencyKey, request, cardStatusFailure);
        }

        // 4. 시뮬레이션 적용 (지연은 트랜잭션 종료 후 wrapper에서)
        SimulatorConfig config = loadConfig();
        boolean approved = simulate(config, card);

        // 5. 가승인 번호 생성 및 DB 갱신
        SimResponseCode rc = approved ? SimResponseCode.PAYMENT_SUCCESS : simulateRejectReasonResolver.resolve(card);
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

    // [be] 하지혁 260603 PreApproval API 2 : 가승인 취소
    @Transactional
    public PreApprovalCancelResponse cancel(String idempotencyKey, PreApprovalCancelRequest request) {
        // 1. idempotency-Key 기반 중복 요청 검사 (취소건)
        var existingCancel = preApprovalRepository.findByCancelIdempotencyKey(idempotencyKey);
        // 중복 시 echo
        if (existingCancel.isPresent()) {
            return toCancelResponse(existingCancel.get(), request.pgTxnId());
        }

        // 2. 원 가승인 조회
        var originOpt = preApprovalRepository.findByAuthorizeIdempotencyKey(request.originIdempotencyKey());
        // 원거래 미존재 예외처리
        if (originOpt.isEmpty()) {
            return cancelFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }
        SimulatorPreApproval origin = originOpt.get();
        // 원거래 상태 예외처리 (AUTHORIZED만 취소 가능)
        if (origin.getPreApprovalStatus() != PreApprovalStatus.AUTHORIZED) {
            return cancelFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 3. 원거래 일관성 검증 (PG 거래 ID, 가승인 번호)
        if (!origin.getPgTxnId().equals(request.originPgTxnId())
                || !origin.getPreApprovalNumber().equals(request.preApprovalNumber())) {
            return cancelFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 4. 토큰 일치 검증
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        if (token == null || !token.getCardId().equals(origin.getCardId())) {
            return cancelFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 5. 가승인 취소 및 DB 갱신 (status=CANCELED, cancel_idempotency_key 저장)
        SimResponseCode rc = SimResponseCode.PAYMENT_SUCCESS;
        try {
            origin.cancel(idempotencyKey, rc.getResponseCode(), rc.getResponseMessage());
            preApprovalRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 동시 취소 요청 예외처리
            var again = preApprovalRepository.findByCancelIdempotencyKey(idempotencyKey);
            if (again.isPresent()) {
                return toCancelResponse(again.get(), request.pgTxnId());
            }
            throw e;
        }
        return toCancelResponse(origin, request.pgTxnId());
    }

    // [be] 하지혁 260604 PreApproval API 4 : 가승인 캡쳐 (가승인 → 결제 확정)
    @Transactional
    public PreApprovalCaptureResponse capture(String idempotencyKey, PreApprovalCaptureRequest request) {
        // 1. capture idempotency-Key 기반 중복 요청 검사 (payment_history에 저장됨)
        var existingCapture = paymentHistoryRepository.findByIdempotencyKey(idempotencyKey);
        if (existingCapture.isPresent()) {
            return toCaptureResponse(existingCapture.get());
        }

        // 2. 동일 origin 중복 capture 방어
        var existingByOrigin = paymentHistoryRepository
                .findByOriginIdempotencyKeyAndPaymentStatus(request.originIdempotencyKey(), PaymentStatus.APPROVED);
        if (existingByOrigin.isPresent()) {
            return captureFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_ALREADY_PROCESSED);
        }

        // 3. 원 가승인 조회
        var originOpt = preApprovalRepository.findByAuthorizeIdempotencyKey(request.originIdempotencyKey());
        // 원거래 미존재 예외처리
        if (originOpt.isEmpty()) {
            return captureFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }
        SimulatorPreApproval origin = originOpt.get();
        // 원거래 상태 예외처리 (AUTHORIZED만 capture 가능)
        if (origin.getPreApprovalStatus() != PreApprovalStatus.AUTHORIZED) {
            return captureFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 4. 원거래 일관성 검증 (PG 거래 ID, 가승인 번호)
        if (!origin.getPgTxnId().equals(request.originPgTxnId())
                || !origin.getPreApprovalNumber().equals(request.preApprovalNumber())) {
            return captureFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 5. 토큰 일치 검증
        SimulatorCardToken token = findActiveToken(request.cardCompany(), request.cardToken());
        if (token == null || !token.getCardId().equals(origin.getCardId())) {
            return captureFailureResponse(idempotencyKey, request, SimResponseCode.TRANSACTION_NOT_FOUND);
        }

        // 6. 카드 상태 재검증 (capture 시점에도 LOST/EXPIRED/DELETED 차단)
        SimulatorCard card = cardRepository.findById(origin.getCardId()).orElse(null);
        if (card == null) {
            return captureFailureResponse(idempotencyKey, request, SimResponseCode.PAYMENT_CARD_NOT_FOUND);
        }
        SimResponseCode cardStatusFailure = switch (card.getCardStatus()) {
            case ACTIVE -> null;
            case LOST -> SimResponseCode.PAYMENT_CARD_LOST;
            case EXPIRED -> SimResponseCode.PAYMENT_CARD_EXPIRED;
            case DELETED -> SimResponseCode.PAYMENT_CARD_DELETED;
        };
        if (cardStatusFailure != null) {
            return captureFailureResponse(idempotencyKey, request, cardStatusFailure);
        }

        // 7. 가승인 상태 전이 (AUTHORIZED → CAPTURED) + payment_history INSERT
        SimResponseCode rc = SimResponseCode.PAYMENT_SUCCESS;
        origin.capture(rc.getResponseCode(), rc.getResponseMessage());
        SimulatorPaymentHistory captured = insertCapturePayment(idempotencyKey, request, origin, rc);
        return toCaptureResponse(captured);
    }

    // [be] 하지혁 260603 PreApproval API 3 : 가승인 조회
    @Transactional(readOnly = true)
    public PreApprovalInquireResponse inquire(PreApprovalInquireRequest request) {
        // 카드사는 실제로 서로 독립된 시스템이므로, 본인의 PG/카드사 영역 외 거래는 조회 불가
        var found = preApprovalRepository.findByPgIdAndCardCompanyAndAuthorizeIdempotencyKey(
                request.pgId(), request.cardCompany(), request.targetIdempotencyKey());
        // 미존재 시 예외처리
        if (found.isEmpty()) {
            SimResponseCode rc = SimResponseCode.TRANSACTION_NOT_FOUND;
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
        SimResponseCode rc = SimResponseCode.ofCode(row.getResponseCode());
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

    // ACTIVE 토큰 조회 (API 1,2)
    private SimulatorCardToken findActiveToken(CardCompany cardCompany, String plainCardToken) {
        String encryptedToken = aesCryptoUtil.encrypt(plainCardToken);
        return tokenRepository
                .findByCardCompanyAndCardTokenAndTokenStatus(cardCompany, encryptedToken, TokenStatus.ACTIVE)
                .orElse(null);
    }

    // 고유 가승인 번호 채번 후 INSERT, 충돌 시 재시도 (API 1)
    private SimulatorPreApproval insertWithUniquePreApprovalNumber(
            String idempotencyKey,
            Supplier<SimulatorPreApproval.SimulatorPreApprovalBuilder> builderSupplier) {
        for (int attempt = 0; attempt < APPROVAL_NUMBER_MAX_RETRY; attempt++) {
            try {
                return preApprovalRepository.save(builderSupplier.get()
                        .preApprovalNumber(RandomStringGenerator.generateHex(APPROVAL_NUMBER_LENGTH))
                        .build());
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 동일 idempotency_key 선행 INSERT → 기존 row echo
                var existing = preApprovalRepository.findByAuthorizeIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    return existing.get();
                }
                // pre_approval_number 충돌로 간주, 새 번호로 재시도
                if (attempt == APPROVAL_NUMBER_MAX_RETRY - 1) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("pre_approval_number 생성 재시도 실패");
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
    private PreApprovalResponse failureResponseWithoutRow(String idempotencyKey, PreApprovalRequest request,
                                                          SimResponseCode rc) {
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

    // 취소 처리 실패 시 에러 응답 (API 2)
    private PreApprovalCancelResponse cancelFailureResponse(String idempotencyKey, PreApprovalCancelRequest request,
                                                            SimResponseCode rc) {
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

    // row → 가승인 응답 매핑 (API 1)
    private PreApprovalResponse toAuthorizedResponse(SimulatorPreApproval row) {
        SimResponseCode rc = SimResponseCode.ofCode(row.getResponseCode());
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

    // row → 가승인 취소 응답 매핑 (API 2)
    private PreApprovalCancelResponse toCancelResponse(SimulatorPreApproval row, Long cancelPgTxnId) {
        SimResponseCode rc = SimResponseCode.ofCode(row.getResponseCode());
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

    // 캡쳐 결제 row INSERT. approval_number는 원 가승인 번호 그대로 사용, 충돌 시 새 번호 채번 (API 4)
    private SimulatorPaymentHistory insertCapturePayment(String captureKey,
                                                         PreApprovalCaptureRequest request,
                                                         SimulatorPreApproval origin,
                                                         SimResponseCode rc) {
        String approvalNumber = origin.getPreApprovalNumber();
        for (int attempt = 0; attempt < APPROVAL_NUMBER_MAX_RETRY; attempt++) {
            try {
                return paymentHistoryRepository.save(SimulatorPaymentHistory.builder()
                        .cardId(origin.getCardId())
                        .cardCompany(origin.getCardCompany())
                        .pgId(request.pgId())
                        .pgTxnId(request.pgTxnId())
                        .originPgTxnId(origin.getPgTxnId())
                        .idempotencyKey(captureKey)
                        .originIdempotencyKey(origin.getAuthorizeIdempotencyKey())
                        .paymentStatus(PaymentStatus.APPROVED)
                        .originalAmount(origin.getOriginalAmount())
                        .approvedAmount(origin.getApprovedAmount())
                        .performanceDate(LocalDateTime.now())
                        .approvalNumber(approvalNumber)
                        .responseCode(rc.getResponseCode())
                        .responseMessage(rc.getResponseMessage())
                        .build());
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 동일 idempotency_key 선행 INSERT → 기존 row echo
                var existing = paymentHistoryRepository.findByIdempotencyKey(captureKey);
                if (existing.isPresent()) {
                    return existing.get();
                }
                // approval_number 충돌로 간주, 새 번호로 재시도
                approvalNumber = RandomStringGenerator.generateHex(APPROVAL_NUMBER_LENGTH);
                if (attempt == APPROVAL_NUMBER_MAX_RETRY - 1) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("capture payment approval_number 생성 재시도 실패");
    }

    // 캡쳐 처리 실패 시 에러 응답 (API 4)
    private PreApprovalCaptureResponse captureFailureResponse(String idempotencyKey,
                                                              PreApprovalCaptureRequest request,
                                                              SimResponseCode rc) {
        return PreApprovalCaptureResponse.builder()
                .pgId(request.pgId())
                .idempotencyKey(idempotencyKey)
                .pgTxnId(request.pgTxnId())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // payment_history row → 캡쳐 응답 매핑 (API 4)
    private PreApprovalCaptureResponse toCaptureResponse(SimulatorPaymentHistory row) {
        SimResponseCode rc = SimResponseCode.ofCode(row.getResponseCode());
        return PreApprovalCaptureResponse.builder()
                .pgId(row.getPgId())
                .idempotencyKey(row.getIdempotencyKey())
                .pgTxnId(row.getPgTxnId())
                .paymentStatus(row.getPaymentStatus())
                .approvalNumber(row.getApprovalNumber())
                .approvedAt(format(row.getCreatedAt()))
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
