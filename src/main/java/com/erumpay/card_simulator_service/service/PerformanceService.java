package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.dto.api.request.PerformanceInquireRequest;
import com.erumpay.card_simulator_service.dto.api.response.PerformanceInquireResponse;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCard.CardStatus;
import com.erumpay.card_simulator_service.entity.SimulatorCardProduct;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.Category;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
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
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final SimulatorUserRepository userRepository;
    private final SimulatorCardProductRepository productRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorPaymentHistoryRepository paymentRepository;
    private final ResponseCodeResolver responseCodeResolver;
    private final AesCryptoUtil aesCryptoUtil;

    @Transactional(readOnly = true)
    public PerformanceInquireResponse inquire(PerformanceInquireRequest request) {
        // 1. 사용자 인증: name(평문) + phone_number(ECB) 일치
        String encryptedPhone = aesCryptoUtil.encrypt(request.phoneNumber());
        SimulatorUser user = userRepository.findByNameAndPhoneNumber(request.name(), encryptedPhone).orElse(null);
        if (user == null) {
            return failureResponse(request, Category.USER, ResponseType.USER_INVALID_INFO);
        }

        // 2. 카드 상품 식별: card_company + product_name
        SimulatorCardProduct product = productRepository
                .findByCardCompanyAndProductName(request.cardCompany(), request.productName()).orElse(null);
        if (product == null) {
            return failureResponse(request, Category.CARD, ResponseType.CARD_INVALID_INFO);
        }

        // 3. 사용자 카드 유효성: user_id + product_id + ACTIVE
        SimulatorCard card = cardRepository
                .findByUserIdAndProductIdAndCardStatus(user.getUserId(), product.getProductId(), CardStatus.ACTIVE)
                .orElse(null);
        if (card == null) {
            return failureResponse(request, Category.CARD, ResponseType.CARD_INVALID_INFO);
        }

        // 4. 누적 금액 집계 (해당 월 performance_date 범위, APPROVED − CANCELED)
        YearMonth period = parsePeriod(request.inquiryPeriod());
        if (period == null) {
            return failureResponse(request, Category.CARD, ResponseType.CARD_INVALID_INFO);
        }
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();

        long approvedSum = paymentRepository.sumApprovedAmount(
                card.getCardId(), PaymentStatus.APPROVED, start, end);
        long canceledSum = paymentRepository.sumApprovedAmount(
                card.getCardId(), PaymentStatus.CANCELED, start, end);
        long currentAmount = approvedSum - canceledSum;

        // 5. 성공 응답
        SimulatorResponseCode rc = responseCodeResolver.resolve(Category.CARD, ResponseType.SUCCESS);
        return PerformanceInquireResponse.builder()
                .cardCompany(request.cardCompany())
                .productName(request.productName())
                .inquiryPeriod(request.inquiryPeriod())
                .currentAmount(currentAmount)
                .responseCode(rc.getResponseCode())
                .responseMessage(rc.getResponseMessage())
                .build();
    }

    private YearMonth parsePeriod(String inquiryPeriod) {
        try {
            return YearMonth.parse(inquiryPeriod, PERIOD_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private PerformanceInquireResponse failureResponse(PerformanceInquireRequest request,
                                                       Category category, ResponseType responseType) {
        SimulatorResponseCode rc = responseCodeResolver.resolve(category, responseType);
        return PerformanceInquireResponse.builder()
                .cardCompany(request.cardCompany())
                .productName(request.productName())
                .inquiryPeriod(request.inquiryPeriod())
                .responseCode(rc.getResponseCode())
                .responseMessage(rc.getResponseMessage())
                .build();
    }
}
