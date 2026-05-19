package com.erumpay.card_simulator_service.seed;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.common.PasswordHashUtil;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardProduct;
import com.erumpay.card_simulator_service.entity.SimulatorConfig;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
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
        List<SimulatorResponseCode> codes = new ArrayList<>();
        for (CardCompany company : seedCardCompanies()) {
            codes.add(responseCode(company, "00", "정상 처리되었습니다.", ResponseType.SUCCESS));
            codes.add(responseCode(company, "01", "카드 정보가 일치하지 않습니다.", ResponseType.CARD_INVALID_INFO));
            codes.add(responseCode(company, "02", "비밀번호가 일치하지 않습니다.", ResponseType.CARD_INVALID_PASSWORD));
            codes.add(responseCode(company, "03", "토큰을 찾을 수 없습니다.", ResponseType.TOKEN_NOT_FOUND));
            codes.add(responseCode(company, "04", "결제가 거절되었습니다.", ResponseType.PAYMENT_REJECTED));
        }
        responseCodeRepository.saveAll(codes);
    }

    private SimulatorResponseCode responseCode(CardCompany company, String code, String message, ResponseType type) {
        return SimulatorResponseCode.builder()
                .cardCompany(company)
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
        // 사용자별 카드사 3개씩 배분 (총 9장)
        List<SimulatorCard> cards = new ArrayList<>();
        cards.addAll(cardsForUser(users.get(0), products, "1234561234567890", CardCompany.SHINHAN));
        cards.addAll(cardsForUser(users.get(0), products, "2345671234567890", CardCompany.SAMSUNG));
        cards.addAll(cardsForUser(users.get(0), products, "3456781234567890", CardCompany.HYUNDAI));
        cards.addAll(cardsForUser(users.get(1), products, "4567891234567890", CardCompany.KB));
        cards.addAll(cardsForUser(users.get(1), products, "5678901234567890", CardCompany.LOTTE));
        cards.addAll(cardsForUser(users.get(1), products, "6789011234567890", CardCompany.WOORI));
        cards.addAll(cardsForUser(users.get(2), products, "7890121234567890", CardCompany.HANA));
        cards.addAll(cardsForUser(users.get(2), products, "8901231234567890", CardCompany.NH));
        cards.addAll(cardsForUser(users.get(2), products, "9012341234567890", CardCompany.BC));
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
        // 1234-56**-****-7890 형식
        return cardNumber.substring(0, 4) + "-"
                + cardNumber.substring(4, 6) + "**-****-"
                + cardNumber.substring(12, 16);
    }

    private List<CardCompany> seedCardCompanies() {
        return List.of(
                CardCompany.SHINHAN, CardCompany.SAMSUNG, CardCompany.HYUNDAI,
                CardCompany.KB, CardCompany.LOTTE, CardCompany.WOORI,
                CardCompany.HANA, CardCompany.NH, CardCompany.BC
        );
    }
}
