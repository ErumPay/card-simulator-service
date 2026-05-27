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
import java.util.List;

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
        seedCardsAndProducts(users);

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
                responseCode(Category.TOKEN, "103", "이미 삭제된 토큰입니다.", ResponseType.TOKEN_ALREADY_DELETED),
                responseCode(Category.TOKEN, "104", "발급 이력을 찾을 수 없습니다.", ResponseType.TOKEN_ISSUE_NOT_FOUND),

                responseCode(Category.CARD, "200", "정상 처리되었습니다.", ResponseType.SUCCESS),
                responseCode(Category.CARD, "201", "분실 신고된 카드입니다.", ResponseType.CARD_LOST),
                responseCode(Category.CARD, "202", "만료된 카드입니다.", ResponseType.CARD_EXPIRED),
                responseCode(Category.CARD, "203", "해지된 카드입니다.", ResponseType.CARD_DELETED),
                responseCode(Category.CARD, "205", "비밀번호가 일치하지 않습니다.", ResponseType.CARD_INVALID_PASSWORD),
                responseCode(Category.CARD, "206", "존재하지 않는 카드입니다.", ResponseType.CARD_NOT_FOUND),
                responseCode(Category.CARD, "207", "카드 유효기간이 일치하지 않습니다.", ResponseType.CARD_INVALID_EXPIRY),
                responseCode(Category.CARD, "208", "카드 보안코드(CVC)가 일치하지 않습니다.", ResponseType.CARD_INVALID_CVC),

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
        List<SimulatorUser> users = new ArrayList<>();
        for (UserSeed seed : USER_SEEDS) {
            users.add(user(seed.name(), seed.phoneNumber(), seed.birthDate()));
        }
        return userRepository.saveAll(users);
    }

    private SimulatorUser user(String name, String phoneNumber, String birthDate) {
        return SimulatorUser.builder()
                .name(name)
                .phoneNumber(aesCryptoUtil.encrypt(phoneNumber))
                .birthDate(aesCryptoUtil.encrypt(birthDate))
                .build();
    }

    private void seedCardsAndProducts(List<SimulatorUser> users) {
        // 사용자별 카드 3장씩 = 총 30장. 카드사는 mock_bin 80~88 정합 유지.
        // 모든 (cardCompany, productName) 조합을 1회씩 product로 시드하고, 그 product에 카드를 매핑.
        List<SimulatorCard> cards = new ArrayList<>();
        for (int i = 0; i < USER_SEEDS.size(); i++) {
            SimulatorUser user = users.get(i);
            for (CardSeed seed : USER_SEEDS.get(i).cards()) {
                SimulatorCardProduct product = productRepository.save(
                        SimulatorCardProduct.builder()
                                .cardCompany(seed.company())
                                .productName(seed.productName())
                                .build()
                );
                cards.add(buildCard(user, product, seed.cardNumber()));
            }
        }
        cardRepository.saveAll(cards);
    }

    private SimulatorCard buildCard(SimulatorUser user, SimulatorCardProduct product, String cardNumber) {
        String salt = PasswordHashUtil.generateSalt();
        return SimulatorCard.builder()
                .userId(user.getUserId())
                .productId(product.getProductId())
                .cardCompany(product.getCardCompany())
                .cardNumber(aesCryptoUtil.encrypt(cardNumber))
                .maskedNumber(toMaskedNumber(cardNumber))
                .expiryDate(aesCryptoUtil.encrypt("2912"))
                .cvc(aesCryptoUtil.encrypt("123"))
                .password2digit(PasswordHashUtil.hash("12", salt))
                .cardSalt(salt)
                .cardStatus(SimulatorCard.CardStatus.ACTIVE)
                .build();
    }

    private String toMaskedNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            throw new IllegalArgumentException("Card number must be exactly 16 digits, got: "
                    + (cardNumber == null ? "null" : cardNumber.length()));
        }
        // 1234-****-****-7890 형식 (앞 4 + 가운데 8 마스킹 + 뒤 4)
        return cardNumber.substring(0, 4) + "-****-****-"
                + cardNumber.substring(12, 16);
    }

    private record CardSeed(CardCompany company, String productName, String cardNumber) {}

    private record UserSeed(String name, String phoneNumber, String birthDate, List<CardSeed> cards) {}

    // 사용자 10명 × 카드 3장씩 = 30장.
    // 이름: 가-차 자음 슬라이딩(가나다, 나다라, …, 차카타)
    // 카드사 분배 라운드 로빈: (삼성/신한/현대) → (KB/롯데/우리) → (하나/NH/IBK) 반복
    // 카드번호 = mock_bin(6, init 11과 정합) + 1234567890 → 16자리
    private static final List<UserSeed> USER_SEEDS = List.of(
            new UserSeed("가나다", "01011111111", "900101", List.of(
                    new CardSeed(CardCompany.SAMSUNG, "삼성 iD SELECT ALL 카드",       "8000111234567890"),
                    new CardSeed(CardCompany.SHINHAN, "신한카드 Mr.Life",               "8100001234567890"),
                    new CardSeed(CardCompany.HYUNDAI, "현대카드ZERO Edition3(할인형)",  "8200001234567890")
            )),
            new UserSeed("나다라", "01022222222", "910202", List.of(
                    new CardSeed(CardCompany.KB,      "KB국민 My WE:SH 카드",           "8300061234567890"),
                    new CardSeed(CardCompany.LOTTE,   "LOCA 365 카드",                  "8400041234567890"),
                    new CardSeed(CardCompany.WOORI,   "카드의정석 SHOPPING+",           "8500021234567890")
            )),
            new UserSeed("다라마", "01033333333", "920303", List.of(
                    new CardSeed(CardCompany.HANA,    "JADE Classic",                   "8600021234567890"),
                    new CardSeed(CardCompany.NH,      "올바른 FLEX 카드",               "8700011234567890"),
                    new CardSeed(CardCompany.IBK,     "K-패스 (신용)",                  "8800021234567890")
            )),
            new UserSeed("라마바", "01044444444", "930404", List.of(
                    new CardSeed(CardCompany.SAMSUNG, "삼성카드 taptap O",              "8000011234567890"),
                    new CardSeed(CardCompany.SHINHAN, "신한카드 Deep Oil",              "8100031234567890"),
                    new CardSeed(CardCompany.HYUNDAI, "현대카드ZERO Edition3(포인트형)","8200011234567890")
            )),
            new UserSeed("마바사", "01055555555", "940505", List.of(
                    new CardSeed(CardCompany.KB,      "굿데이카드",                     "8300001234567890"),
                    new CardSeed(CardCompany.LOTTE,   "LOCA LIKIT 1.2",                 "8400011234567890"),
                    new CardSeed(CardCompany.WOORI,   "카드의정석2",                    "8500071234567890")
            )),
            new UserSeed("바사아", "01066666666", "950606", List.of(
                    new CardSeed(CardCompany.HANA,    "트래블로그 신용카드",            "8600011234567890"),
                    new CardSeed(CardCompany.NH,      "zgm.streaming카드",              "8700031234567890"),
                    new CardSeed(CardCompany.IBK,     "I-ALL",                          "8800011234567890")
            )),
            new UserSeed("사아자", "01077777777", "960707", List.of(
                    new CardSeed(CardCompany.SAMSUNG, "삼성카드 & MILEAGE PLATINUM (스카이패스)", "8000001234567890"),
                    new CardSeed(CardCompany.SHINHAN, "신한카드 Discount Plan+",        "8100131234567890"),
                    new CardSeed(CardCompany.HYUNDAI, "현대카드 M",                     "8200041234567890")
            )),
            new UserSeed("아자차", "01088888888", "970808", List.of(
                    new CardSeed(CardCompany.KB,      "쿠팡 와우카드",                  "8300081234567890"),
                    new CardSeed(CardCompany.LOTTE,   "LOCA LIKIT",                     "8400001234567890"),
                    new CardSeed(CardCompany.WOORI,   "카드의정석 EVERY DISCOUNT",      "8500061234567890")
            )),
            new UserSeed("자차카", "01099999999", "980909", List.of(
                    new CardSeed(CardCompany.HANA,    "토스뱅크 하나카드 Day",          "8600041234567890"),
                    new CardSeed(CardCompany.NH,      "zgm.play카드",                   "8700041234567890"),
                    new CardSeed(CardCompany.IBK,     "IBK포인트(신용)",                "8800051234567890")
            )),
            new UserSeed("차카타", "01010101010", "991010", List.of(
                    new CardSeed(CardCompany.SAMSUNG, "삼성 iD GLOBAL 카드",            "8000101234567890"),
                    new CardSeed(CardCompany.SHINHAN, "신한카드 Air One",               "8100041234567890"),
                    new CardSeed(CardCompany.HYUNDAI, "현대카드T",                      "8200131234567890")
            ))
    );
}
