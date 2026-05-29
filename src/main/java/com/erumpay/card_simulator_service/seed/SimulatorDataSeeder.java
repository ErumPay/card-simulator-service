package com.erumpay.card_simulator_service.seed;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.IinMapping;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "simulator.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SimulatorDataSeeder implements CommandLineRunner {

    // CSV 컬럼 순서: 0 이름, 1 전화번호, 2 생년월일, 3 카드사(참고용), 4 카드상품명(참고용),
    //               5 카드번호, 6 CVC, 7 만료일, 8 카드비밀번호, 9 카드상태
    private static final int COL_NAME = 0;
    private static final int COL_PHONE = 1;
    private static final int COL_BIRTH = 2;
    private static final int COL_CARD_NUMBER = 5;
    private static final int COL_CVC = 6;
    private static final int COL_EXPIRY = 7;
    private static final int COL_PASSWORD = 8;
    private static final int COL_STATUS = 9;
    private static final int MIN_COLUMNS = 10;
    private static final char BOM = 0xFEFF;

    private final SimulatorUserRepository userRepository;
    private final SimulatorCardProductRepository productRepository;
    private final SimulatorCardRepository cardRepository;
    private final SimulatorResponseCodeRepository responseCodeRepository;
    private final SimulatorConfigRepository configRepository;
    private final AesCryptoUtil aesCryptoUtil;

    // 사용자/카드 시드는 .env 와 동일 위치(프로젝트 루트)의 seed.csv 에서 읽는다.
    @Value("${simulator.seed.csv-path:seed.csv}")
    private String csvPath;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Simulator data already seeded. Skip.");
            return;
        }

        seedConfig();
        seedResponseCodes();
        List<SimulatorCardProduct> products = seedProducts();

        List<CardRow> rows = readSeedCsv();
        Map<String, SimulatorUser> usersByKey = seedUsers(rows);
        seedCards(rows, usersByKey, products);

        log.info("Simulator data seeded successfully. users={}, products={}, cards={}",
                usersByKey.size(), products.size(), rows.size());
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

    private List<SimulatorCardProduct> seedProducts() {
        List<SimulatorCardProduct> products = new ArrayList<>();
        for (CardProductCatalog.ProductSeed seed : CardProductCatalog.PRODUCTS) {
            products.add(SimulatorCardProduct.builder()
                    .cardCompany(IinMapping.findByCardNumber(seed.mockBin()))
                    .productName(seed.productName())
                    .build());
        }
        return productRepository.saveAll(products);
    }

    // CSV 의 (이름,전화,생년월일) 조합별로 사용자 1명을 생성한다. 등장 순서를 보존한다.
    private Map<String, SimulatorUser> seedUsers(List<CardRow> rows) {
        Map<String, SimulatorUser> distinct = new LinkedHashMap<>();
        for (CardRow row : rows) {
            distinct.computeIfAbsent(row.userKey(), k -> user(row.name(), row.phoneNumber(), row.birthDate()));
        }
        List<SimulatorUser> saved = userRepository.saveAll(new ArrayList<>(distinct.values()));

        Map<String, SimulatorUser> usersByKey = new LinkedHashMap<>();
        int i = 0;
        for (String key : distinct.keySet()) {
            usersByKey.put(key, saved.get(i++));
        }
        return usersByKey;
    }

    private SimulatorUser user(String name, String phoneNumber, String birthDate) {
        return SimulatorUser.builder()
                .name(name)
                .phoneNumber(aesCryptoUtil.encrypt(phoneNumber))
                .birthDate(aesCryptoUtil.encrypt(birthDate))
                .build();
    }

    // 카드는 CSV 행 그대로 생성한다. 카드↔상품 매핑은 카드번호 앞 6자리(mockBin)로 카탈로그 상품을 찾는다.
    private void seedCards(List<CardRow> rows, Map<String, SimulatorUser> usersByKey,
                           List<SimulatorCardProduct> products) {
        Map<String, SimulatorCardProduct> productByBin = new HashMap<>();
        for (int i = 0; i < products.size(); i++) {
            productByBin.put(CardProductCatalog.PRODUCTS.get(i).mockBin(), products.get(i));
        }

        List<SimulatorCard> cards = new ArrayList<>();
        for (CardRow row : rows) {
            SimulatorUser owner = usersByKey.get(row.userKey());
            String bin = row.cardNumber().substring(0, 6);
            SimulatorCardProduct product = productByBin.get(bin);
            if (product == null) {
                throw new IllegalStateException("No card product found for mockBin: " + bin
                        + " (cardNumber=" + row.cardNumber() + ")");
            }
            cards.add(buildCard(owner, product, row));
        }
        cardRepository.saveAll(cards);
    }

    private SimulatorCard buildCard(SimulatorUser user, SimulatorCardProduct product, CardRow row) {
        String salt = PasswordHashUtil.generateSalt();
        return SimulatorCard.builder()
                .userId(user.getUserId())
                .productId(product.getProductId())
                .cardCompany(product.getCardCompany())
                .cardNumber(aesCryptoUtil.encrypt(row.cardNumber()))
                .maskedNumber(toMaskedNumber(row.cardNumber()))
                .expiryDate(aesCryptoUtil.encrypt(row.expiryDate()))
                .cvc(aesCryptoUtil.encrypt(row.cvc()))
                .password2digit(PasswordHashUtil.hash(row.password(), salt))
                .cardSalt(salt)
                .cardStatus(row.status())
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

    private List<CardRow> readSeedCsv() {
        Path path = Path.of(csvPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Seed CSV not found: " + path.toAbsolutePath()
                    + " (simulator.seed.csv-path)");
        }

        List<CardRow> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo == 1) {
                    continue; // 헤더 스킵
                }
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                rows.add(parseRow(stripBom(line), lineNo));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read seed CSV: " + path.toAbsolutePath(), e);
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException("Seed CSV has no data rows: " + path.toAbsolutePath());
        }
        return rows;
    }

    private CardRow parseRow(String line, int lineNo) {
        List<String> cols = parseCsvLine(line);
        if (cols.size() < MIN_COLUMNS) {
            throw new IllegalStateException("Seed CSV line " + lineNo + " must have at least "
                    + MIN_COLUMNS + " columns, got " + cols.size() + ": " + line);
        }
        String cardNumber = cols.get(COL_CARD_NUMBER).trim();
        if (cardNumber.length() != 16) {
            throw new IllegalStateException("Seed CSV line " + lineNo
                    + " card number must be 16 digits: " + cardNumber);
        }
        return new CardRow(
                cols.get(COL_NAME).trim(),
                cols.get(COL_PHONE).trim(),
                cols.get(COL_BIRTH).trim(),
                cardNumber,
                cols.get(COL_CVC).trim(),
                cols.get(COL_EXPIRY).trim(),
                cols.get(COL_PASSWORD).trim(),
                parseStatus(cols.get(COL_STATUS).trim(), lineNo)
        );
    }

    private SimulatorCard.CardStatus parseStatus(String raw, int lineNo) {
        if (!StringUtils.hasText(raw)) {
            return SimulatorCard.CardStatus.ACTIVE;
        }
        try {
            return SimulatorCard.CardStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Seed CSV line " + lineNo
                    + " has unknown card status: " + raw, e);
        }
    }

    // 쌍따옴표(필드 내 콤마/escape) 를 지원하는 최소 CSV 파서.
    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result;
    }

    private static String stripBom(String s) {
        return (!s.isEmpty() && s.charAt(0) == BOM) ? s.substring(1) : s;
    }

    private record CardRow(String name, String phoneNumber, String birthDate, String cardNumber,
                           String cvc, String expiryDate, String password, SimulatorCard.CardStatus status) {
        String userKey() {
            return name + "|" + phoneNumber + "|" + birthDate;
        }
    }
}
