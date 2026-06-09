package com.erumpay.card_simulator_service.seed;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.IinMapping;
import com.erumpay.card_simulator_service.common.PasswordHashUtil;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorCardProduct;
import com.erumpay.card_simulator_service.entity.SimulatorConfig;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory;
import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import com.erumpay.card_simulator_service.entity.SimulatorUser;
import com.erumpay.card_simulator_service.repository.SimulatorCardProductRepository;
import com.erumpay.card_simulator_service.repository.SimulatorCardRepository;
import com.erumpay.card_simulator_service.repository.SimulatorConfigRepository;
import com.erumpay.card_simulator_service.repository.SimulatorPaymentHistoryRepository;
import com.erumpay.card_simulator_service.repository.SimulatorUserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    private final SimulatorConfigRepository configRepository;
    private final SimulatorPaymentHistoryRepository paymentRepository;
    private final AesCryptoUtil aesCryptoUtil;
    private final ObjectMapper objectMapper;

    // 사용자/카드 시드는 .env 와 동일 위치(프로젝트 루트)의 seed.csv 에서 읽는다.
    @Value("${simulator.seed.csv-path:seed.csv}")
    private String csvPath;

    // tier 임계값 입력 (extract_tier_thresholds.py 산출물)
    @Value("${simulator.seed.tier-thresholds-path:tier-thresholds.json}")
    private String tierThresholdsPath;

    // 결제이력 시드 결과 요약 (Postman 검증 정답표)
    @Value("${simulator.seed.performance-summary-path:performance-seed-summary.csv}")
    private String performanceSummaryPath;

    @Override
    @Transactional
    public void run(String... args) {
        boolean userFresh = userRepository.count() == 0;
        boolean paymentEmpty = paymentRepository.count() == 0;

        if (userFresh) {
            seedConfig();
            List<SimulatorCardProduct> products = seedProducts();

            List<CardRow> rows = readSeedCsv();
            Map<String, SimulatorUser> usersByKey = seedUsers(rows);
            List<SimulatorCard> savedCards = seedCards(rows, usersByKey, products);

            log.info("Simulator base data seeded. users={}, products={}, cards={}",
                    usersByKey.size(), products.size(), savedCards.size());

            if (paymentEmpty) {
                seedPaymentHistory(savedCards, products, new ArrayList<>(usersByKey.values()));
            }
        } else if (paymentEmpty) {
            log.info("User/card already seeded; adding payment history only.");
            List<SimulatorCard> cards = cardRepository.findAll();
            List<SimulatorCardProduct> products = productRepository.findAll();
            List<SimulatorUser> users = userRepository.findAll();
            seedPaymentHistory(cards, products, users);
        } else {
            log.info("Simulator data already seeded (users={}, payments={}). Skip.",
                    userRepository.count(), paymentRepository.count());
        }
    }

    private void seedConfig() {
        configRepository.save(SimulatorConfig.builder()
                .approvalRate(new BigDecimal("100.00"))
                .delayMs(0)
                .rejectPattern(null)
                .build());
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
    private List<SimulatorCard> seedCards(List<CardRow> rows, Map<String, SimulatorUser> usersByKey,
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
        return cardRepository.saveAll(cards);
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

    // ============================================================
    // 결제이력 시드 (실적 조회 API 테스트용)
    // ============================================================

    private static final long DEFAULT_MIN_THRESHOLD = 300_000L;
    private static final long BASE_PG_TXN_ID = 9_000_000_000L; // 시드 전용 high range
    private static final String SEED_PG_ID = "001";
    private static final String SEED_RESPONSE_CODE = "SIM-PAYMENT-300";
    private static final String SEED_RESPONSE_MESSAGE = "정상 처리되었습니다.";
    private static final YearMonth TARGET_PERIOD = YearMonth.of(2026, 5);
    private static final int CARDS_PER_USER = 6;
    private static final long RNG_SEED = 42L;

    private enum Scenario {
        SUFFICIENT_RICH("충족-여유"),
        SUFFICIENT_EDGE("충족-경계"),
        NEAR_MISS("근접-미달"),
        INSUFFICIENT("미달");

        final String label;

        Scenario(String label) {
            this.label = label;
        }
    }

    private record TierData(List<Long> tierMins, List<Long> tierMaxes, boolean hasZeroTier) {
    }

    private void seedPaymentHistory(List<SimulatorCard> cards,
                                    List<SimulatorCardProduct> products,
                                    List<SimulatorUser> users) {
        Map<String, TierData> thresholds = loadTierThresholds();

        Map<Long, SimulatorCardProduct> productById = new HashMap<>();
        for (SimulatorCardProduct p : products) {
            productById.put(p.getProductId(), p);
        }
        Map<Long, SimulatorUser> userById = new HashMap<>();
        for (SimulatorUser u : users) {
            userById.put(u.getUserId(), u);
        }

        // 시드 순서 보장 위해 card_id 오름차순 정렬
        List<SimulatorCard> sorted = cards.stream()
                .sorted(Comparator.comparing(SimulatorCard::getCardId))
                .toList();

        LocalDateTime monthStart = TARGET_PERIOD.atDay(1).atStartOfDay();
        Random rand = new Random(RNG_SEED);
        Scenario[] rotation = Scenario.values();

        List<SimulatorPaymentHistory> payments = new ArrayList<>();
        List<String[]> csvRows = new ArrayList<>();
        long globalSeq = 0L;
        int normalIdx = 0;

        for (int i = 0; i < sorted.size(); i++) {
            SimulatorCard card = sorted.get(i);
            SimulatorCardProduct product = productById.get(card.getProductId());
            SimulatorUser user = userById.get(card.getUserId());

            String key = product.getCardCompany().getDisplayName() + "|" + product.getProductName();
            TierData td = thresholds.get(key);

            // 신규 실물카드(BIN=527289, KB국민카드 노리체크카드) 강제 분기:
            // 4시나리오 로테이션을 건너뛰고 합계 200,000원으로 고정한다.
            // normalIdx 를 건드리지 않아 다른 카드의 시나리오 분배에 영향이 없다.
            boolean realCard527289 = "KB국민카드".equals(product.getCardCompany().getDisplayName())
                    && "노리체크카드".equals(product.getProductName());

            Scenario scenario;
            long minTier;
            long maxTier;
            String note;
            long targetSum;

            if (realCard527289) {
                // normalIdx 도 함께 증가시켜 후속 카드의 시나리오 배치를 보존한다.
                normalIdx++;
                scenario = Scenario.SUFFICIENT_EDGE;
                minTier = 200_000L;
                maxTier = 200_000L;
                targetSum = 200_000L;
                note = "실물카드(527289) 강제 합계 200000";
            } else if (td == null) {
                // no tier data — default 적용 + 4시나리오 로테이션
                scenario = rotation[normalIdx++ % rotation.length];
                minTier = DEFAULT_MIN_THRESHOLD;
                maxTier = DEFAULT_MIN_THRESHOLD;
                targetSum = computeTargetSum(scenario, minTier, maxTier);
                note = "no tier data; default 300000 applied";
            } else if (td.tierMins().isEmpty() && td.hasZeroTier()) {
                // zero-only tier — 실적 무관 카드. 충족-여유 단일 시나리오.
                scenario = Scenario.SUFFICIENT_RICH;
                minTier = 0L;
                maxTier = 0L;
                targetSum = DEFAULT_MIN_THRESHOLD;
                note = "zero-tier only (실적 무관)";
            } else if (td.tierMins().isEmpty()) {
                // 데이터 없음 (방어적 분기)
                scenario = rotation[normalIdx++ % rotation.length];
                minTier = DEFAULT_MIN_THRESHOLD;
                maxTier = DEFAULT_MIN_THRESHOLD;
                targetSum = computeTargetSum(scenario, minTier, maxTier);
                note = "no usable tier; default 300000 applied";
            } else {
                scenario = rotation[normalIdx++ % rotation.length];
                minTier = td.tierMins().get(0);
                maxTier = td.tierMins().get(td.tierMins().size() - 1);
                targetSum = computeTargetSum(scenario, minTier, maxTier);
                note = "";
            }

            int rowCount = 6 + rand.nextInt(5); // 6~10
            long perRow = targetSum / rowCount;
            long remainder = targetSum - perRow * rowCount;

            int userIdx = (i / CARDS_PER_USER) + 1;
            int cardIdx = (i % CARDS_PER_USER) + 1;

            long actualSum = 0L;
            for (int r = 0; r < rowCount; r++) {
                globalSeq++;
                long amount = perRow + (r == 0 ? remainder : 0L);
                actualSum += amount;

                // 5월 1~30일에 균등 분산 (월별 결제 기준)
                int dayOffset = (r * 30) / rowCount;
                LocalDateTime perfDate = monthStart.plusDays(dayOffset).plusHours(12);

                String idem = String.format("SEED-PAY-%02d-%02d-%03d", userIdx, cardIdx, r + 1);
                String approvalNum = String.format("SEEDAPRV%02d%02d%03d", userIdx, cardIdx, r + 1);

                payments.add(SimulatorPaymentHistory.builder()
                        .cardId(card.getCardId())
                        .cardCompany(card.getCardCompany())
                        .pgId(SEED_PG_ID)
                        .pgTxnId(BASE_PG_TXN_ID + globalSeq)
                        .idempotencyKey(idem)
                        .paymentStatus(PaymentStatus.APPROVED)
                        .originalAmount(amount)
                        .approvedAmount(amount)
                        .performanceDate(perfDate)
                        .approvalNumber(approvalNum)
                        .responseCode(SEED_RESPONSE_CODE)
                        .responseMessage(SEED_RESPONSE_MESSAGE)
                        .build());
            }

            csvRows.add(new String[]{
                    user.getName(),
                    aesCryptoUtil.decrypt(user.getPhoneNumber()),
                    product.getCardCompany().getDisplayName(),
                    product.getProductName(),
                    String.valueOf(card.getCardId()),
                    card.getCardStatus().name(),
                    TARGET_PERIOD.format(DateTimeFormatter.ofPattern("yyyyMM")),
                    String.valueOf(minTier),
                    String.valueOf(maxTier),
                    scenario.label,
                    String.valueOf(actualSum),
                    String.valueOf(rowCount),
                    note,
            });
        }

        paymentRepository.saveAll(payments);
        writePerformanceSummaryCsv(csvRows);

        log.info("Payment history seeded: {} rows across {} cards, summary CSV: {}",
                payments.size(), sorted.size(), Path.of(performanceSummaryPath).toAbsolutePath());
    }

    private long computeTargetSum(Scenario s, long minTier, long maxTier) {
        return switch (s) {
            case SUFFICIENT_RICH -> Math.round(maxTier * 1.2);
            case SUFFICIENT_EDGE -> Math.round(minTier * 1.05);
            case NEAR_MISS -> Math.round(minTier * 0.95);
            case INSUFFICIENT -> minTier / 2;
        };
    }

    private Map<String, TierData> loadTierThresholds() {
        Path path = Path.of(tierThresholdsPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Tier thresholds JSON not found: " + path.toAbsolutePath()
                    + " (run extract_tier_thresholds.py first)");
        }
        try {
            Map<String, RawTierEntry> raw = objectMapper.readValue(
                    path.toFile(),
                    new TypeReference<Map<String, RawTierEntry>>() {
                    });
            Map<String, TierData> out = new HashMap<>();
            for (Map.Entry<String, RawTierEntry> e : raw.entrySet()) {
                RawTierEntry r = e.getValue();
                List<Long> mins = r.tier_mins == null ? List.of() : r.tier_mins;
                List<Long> maxes = r.tier_maxes == null ? List.of() : r.tier_maxes;
                out.put(e.getKey(), new TierData(mins, maxes, r.has_zero_tier));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read tier thresholds: " + path.toAbsolutePath(), e);
        }
    }

    // JSON 매핑 전용 (snake_case 그대로). 외부에서 직접 안 씀.
    @SuppressWarnings("checkstyle:MemberName")
    static final class RawTierEntry {
        public String source_card_id;
        public List<Long> tier_mins;
        public List<Long> tier_maxes;
        public boolean has_zero_tier;
        public int tier_count_total;
    }

    private void writePerformanceSummaryCsv(List<String[]> rows) {
        Path path = Path.of(performanceSummaryPath);
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("# Generated by SimulatorDataSeeder. inquiry_period 테스트 시 202605 사용.\n");
            w.write("# card_id_assumed: fresh DB(simulator_db drop+create) 기준 seed.csv 순서 1~48 가정.\n");
            w.write("# 재기동/누적 시 실제 card_id 는 다를 수 있으며, 직접 조정 필요.\n");
            w.write(String.join(",",
                    "user_name", "user_phone", "card_company", "product_name",
                    "card_id_assumed", "card_status", "inquiry_period",
                    "min_tier_threshold", "max_tier_threshold", "scenario",
                    "expected_currentAmount", "row_count", "note"));
            w.write("\n");
            for (String[] row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(csvEscape(row[i]));
                }
                sb.append('\n');
                w.write(sb.toString());
            }
            log.info("Performance summary CSV written: {}", path.toAbsolutePath());
        } catch (IOException e) {
            // CSV 미생성은 시드 자체 성공을 가로막지 않는다 (컨테이너 RO FS 등 환경 이슈).
            log.warn("Failed to write performance summary CSV ({}): {}", path.toAbsolutePath(), e.getMessage());
        }
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needsQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0;
        if (!needsQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
