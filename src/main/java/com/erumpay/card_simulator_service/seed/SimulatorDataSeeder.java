package com.erumpay.card_simulator_service.seed;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.PasswordHashUtil;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardProduct;
import com.erumpay.card_simulator_service.entity.SimulatorConfig;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.Category;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
import com.erumpay.card_simulator_service.entity.SimulatorUser;
import com.erumpay.card_simulator_service.repository.SimulatorCardProductRepository;
import com.erumpay.card_simulator_service.repository.SimulatorCardRepository;
import com.erumpay.card_simulator_service.repository.SimulatorConfigRepository;
import com.erumpay.card_simulator_service.repository.SimulatorResponseCodeRepository;
import com.erumpay.card_simulator_service.repository.SimulatorUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "simulator.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SimulatorDataSeeder implements CommandLineRunner {

    private final SimulatorUserRepository userRepository;
    private final SimulatorCardProductRepository productRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorResponseCodeRepository responseCodeRepository;
    private final SimulatorConfigRepository configRepository;
    private final AesCryptoUtil aesCryptoUtil;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Simulator data already seeded. Skip.");
            return;
        }

        seedConfig();
        seedResponseCodes();
        List<SimulatorUser> users = seedUsers();
        Map<CardCompany, SimulatorCardProduct> productsByCompany = seedProducts();
        seedCards(users, productsByCompany);

        log.info("Simulator data seeded successfully.");
    }

    private void seedConfig() {
        configRepository.save(SimulatorConfig.builder()
                .approvalRate(new BigDecimal("100.00"))
                .delayMs(0)
                .rejectPattern(null)
                .build());
    }

    private void seedResponseCodes() {
        List<SimulatorResponseCode> codes = List.of(
                responseCode(Category.TOKEN, "100", "정상 처리되었습니다.", ResponseType.SUCCESS),
                responseCode(Category.TOKEN, "101", "토큰을 찾을 수 없습니다.", ResponseType.TOKEN_NOT_FOUND),
                responseCode(Category.TOKEN, "102", "이미 발급된 토큰이 존재합니다.", ResponseType.TOKEN_DUPLICATE),

                responseCode(Category.CARD, "200", "정상 처리되었습니다.", ResponseType.SUCCESS),
                responseCode(Category.CARD, "201", "분실 신고된 카드입니다.", ResponseType.CARD_LOST),
                responseCode(Category.CARD, "202", "만료된 카드입니다.", ResponseType.CARD_EXPIRED),
                responseCode(Category.CARD, "203", "해지된 카드입니다.", ResponseType.CARD_DELETED),
                responseCode(Category.CARD, "204", "카드 정보가 일치하지 않습니다.", ResponseType.CARD_INVALID_INFO),
                responseCode(Category.CARD, "205", "비밀번호가 일치하지 않습니다.", ResponseType.CARD_INVALID_PASSWORD),

                responseCode(Category.PAYMENT, "300", "정상 처리되었습니다.", ResponseType.SUCCESS),
                responseCode(Category.PAYMENT, "301", "한도를 초과했습니다.", ResponseType.PAYMENT_LIMIT_EXCEEDED),
                responseCode(Category.PAYMENT, "302", "잔액이 부족합니다.", ResponseType.PAYMENT_INSUFFICIENT_BALANCE),
                responseCode(Category.PAYMENT, "303", "결제가 거절되었습니다.", ResponseType.PAYMENT_REJECTED),

                responseCode(Category.TRANSACTION, "400", "정상 처리되었습니다.", ResponseType.SUCCESS),
                responseCode(Category.TRANSACTION, "401", "거래를 찾을 수 없습니다.", ResponseType.TRANSACTION_NOT_FOUND),
                responseCode(Category.TRANSACTION, "402", "이미 처리된 거래입니다.", ResponseType.TRANSACTION_ALREADY_PROCESSED),

                responseCode(Category.USER, "500", "정상 처리되었습니다.", ResponseType.SUCCESS),
                responseCode(Category.USER, "501", "사용자를 찾을 수 없습니다.", ResponseType.USER_NOT_FOUND),
                responseCode(Category.USER, "502", "본인 정보가 일치하지 않습니다.", ResponseType.USER_INVALID_INFO),

                responseCode(Category.SYSTEM, "999", "시스템 오류가 발생했습니다.", ResponseType.SYSTEM_ERROR)
        );
        responseCodeRepository.saveAll(codes);
    }

    private SimulatorResponseCode responseCode(Category category, String code, String message, ResponseType type) {
        return SimulatorResponseCode.builder()
                .category(category)
                .responseCode(code)
                .responseMessage(message)
                .responseType(type)
                .build();
    }

    private List<SimulatorUser> seedUsers() {
        List<SimulatorUser> users = List.of(
                user("홍길동", "01011111111", "900101"),
                user("김철수", "01022222222", "850515"),
                user("이영희", "01033333333", "950820")
        );
        return userRepository.saveAll(users);
    }

    private SimulatorUser user(String name, String phoneNumber, String birthDate) {
        return SimulatorUser.builder()
                .name(name)
                .phoneNumber(aesCryptoUtil.encrypt(phoneNumber))
                .birthDate(aesCryptoUtil.encrypt(birthDate))
                .build();
    }

    private Map<CardCompany, SimulatorCardProduct> seedProducts() {
        Map<CardCompany, SimulatorCardProduct> map = new EnumMap<>(CardCompany.class);
        for (CardCompany company : seedCardCompanies()) {
            SimulatorCardProduct product = productRepository.save(
                    SimulatorCardProduct.builder()
                            .cardCompany(company)
                            .productName(company.getDisplayName() + " 베이직")
                            .build()
            );
            map.put(company, product);
        }
        return map;
    }

    private void seedCards(List<SimulatorUser> users, Map<CardCompany, SimulatorCardProduct> products) {
        // 사용자별 카드사 3개씩 배분 (총 9장) — mock BIN 체계 80~88 정합
        List<SimulatorCard> cards = new ArrayList<>();
        cards.addAll(cardsForUser(users.get(0), products, "8000001234567890", CardCompany.SAMSUNG));
        cards.addAll(cardsForUser(users.get(0), products, "8100001234567890", CardCompany.SHINHAN));
        cards.addAll(cardsForUser(users.get(0), products, "8200001234567890", CardCompany.HYUNDAI));
        cards.addAll(cardsForUser(users.get(1), products, "8300001234567890", CardCompany.KB));
        cards.addAll(cardsForUser(users.get(1), products, "8400001234567890", CardCompany.LOTTE));
        cards.addAll(cardsForUser(users.get(1), products, "8500001234567890", CardCompany.WOORI));
        cards.addAll(cardsForUser(users.get(2), products, "8600001234567890", CardCompany.HANA));
        cards.addAll(cardsForUser(users.get(2), products, "8700001234567890", CardCompany.NH));
        cards.addAll(cardsForUser(users.get(2), products, "8800001234567890", CardCompany.IBK));
        cardRepository.saveAll(cards);
    }

    private List<SimulatorCard> cardsForUser(SimulatorUser user, Map<CardCompany, SimulatorCardProduct> products,
                                              String cardNumber, CardCompany company) {
        String salt = PasswordHashUtil.generateSalt();
        return List.of(SimulatorCard.builder()
                .userId(user.getUserId())
                .productId(products.get(company).getProductId())
                .cardCompany(company)
                .cardNumber(aesCryptoUtil.encrypt(cardNumber))
                .maskedNumber(toMaskedNumber(cardNumber))
                .expiryDate(aesCryptoUtil.encrypt("2912"))
                .cvc(aesCryptoUtil.encrypt("123"))
                .password2digit(PasswordHashUtil.hash("12", salt))
                .cardSalt(salt)
                .cardStatus(SimulatorCard.CardStatus.ACTIVE)
                .build());
    }

    private String toMaskedNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            throw new IllegalArgumentException("Card number must be exactly 16 digits, got: "
                    + (cardNumber == null ? "null" : cardNumber.length()));
        }
        // 1234-56**-****-7890 형식
        return cardNumber.substring(0, 4) + "-"
                + cardNumber.substring(4, 6) + "**-****-"
                + cardNumber.substring(12, 16);
    }

    private List<CardCompany> seedCardCompanies() {
        return List.of(
                CardCompany.SAMSUNG, CardCompany.SHINHAN, CardCompany.HYUNDAI,
                CardCompany.KB, CardCompany.LOTTE, CardCompany.WOORI,
                CardCompany.HANA, CardCompany.NH, CardCompany.IBK
        );
    }
}
