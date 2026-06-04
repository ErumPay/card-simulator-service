package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.SimResponseCode;
import com.erumpay.card_simulator_service.dto.api.request.PerformanceInquireRequest;
import com.erumpay.card_simulator_service.dto.api.response.PerformanceInquireResponse;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCard.CardStatus;
import com.erumpay.card_simulator_service.entity.SimulatorCardProduct;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import com.erumpay.card_simulator_service.entity.SimulatorUser;
import com.erumpay.card_simulator_service.repository.SimulatorCardProductRepository;
import com.erumpay.card_simulator_service.repository.SimulatorCardRepository;
import com.erumpay.card_simulator_service.repository.SimulatorPaymentHistoryRepository;
import com.erumpay.card_simulator_service.repository.SimulatorUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final SimulatorUserRepository userRepository;
    private final SimulatorCardProductRepository productRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorPaymentHistoryRepository paymentRepository;
    private final AesCryptoUtil aesCryptoUtil;

    // [be] 하지혁 260603 Performance API 1 : 카드 실적 조회
    @Transactional(readOnly = true)
    public PerformanceInquireResponse inquire(PerformanceInquireRequest request) {
        // 1. 사용자 인증 (name 평문 + phone_number ECB 일치)
        String encryptedPhone = aesCryptoUtil.encrypt(request.phoneNumber());
        SimulatorUser user = userRepository.findByNameAndPhoneNumber(request.name(), encryptedPhone).orElse(null);
        // 사용자 미일치 예외처리
        if (user == null) {
            return failureResponse(request, SimResponseCode.USER_PHONE_INVALID);
        }

        // 2. 카드 상품 식별 (card_company + product_name)
        SimulatorCardProduct product = productRepository
                .findByCardCompanyAndProductName(request.cardCompany(), request.productName()).orElse(null);
        // 카드 상품 미존재 예외처리
        if (product == null) {
            return failureResponse(request, SimResponseCode.CARD_NOT_FOUND);
        }

        // 3. 사용자 카드 유효성 검증 (user_id + product_id + ACTIVE)
        SimulatorCard card = cardRepository
                .findByUserIdAndProductIdAndCardStatus(user.getUserId(), product.getProductId(), CardStatus.ACTIVE)
                .orElse(null);
        // 보유 카드 미존재 예외처리
        if (card == null) {
            return failureResponse(request, SimResponseCode.CARD_NOT_FOUND);
        }

        // 4. 누적 금액 집계 (해당 월 performance_date 범위, APPROVED − CANCELED)
        // inquiry_period는 @Pattern으로 유효한 YYYYMM만 통과하므로 파싱 실패는 발생하지 않음
        YearMonth period = YearMonth.parse(request.inquiryPeriod(), PERIOD_FORMATTER);
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();

        long approvedSum = paymentRepository.sumApprovedAmount(
                card.getCardId(), PaymentStatus.APPROVED, start, end);
        long canceledSum = paymentRepository.sumApprovedAmount(
                card.getCardId(), PaymentStatus.CANCELED, start, end);
        long currentAmount = approvedSum - canceledSum;

        // 5. 성공 응답
        SimResponseCode rc = SimResponseCode.CARD_SUCCESS;
        return PerformanceInquireResponse.builder()
                .cardCompany(request.cardCompany())
                .productName(request.productName())
                .inquiryPeriod(request.inquiryPeriod())
                .currentAmount(currentAmount)
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    // 실적 조회 실패 시 에러 응답 (API 1)
    private PerformanceInquireResponse failureResponse(PerformanceInquireRequest request, SimResponseCode rc) {
        return PerformanceInquireResponse.builder()
                .cardCompany(request.cardCompany())
                .productName(request.productName())
                .inquiryPeriod(request.inquiryPeriod())
                .responseHttp(rc.getResponseHttp())
                .responseCode(rc.getResponseCode())
                .responseReason(rc.getResponseReason())
                .responseMessage(rc.getResponseMessage())
                .build();
    }
}
