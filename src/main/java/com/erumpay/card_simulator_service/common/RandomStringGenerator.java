package com.erumpay.card_simulator_service.common;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

public class RandomStringGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private RandomStringGenerator() {}

    public static String generateHex(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        byte[] bytes = new byte[(length + 1) / 2];
        RANDOM.nextBytes(bytes);
        String hex = HEX.formatHex(bytes);
        return hex.length() == length ? hex : hex.substring(0, length);
    }

    public static String generateUuidV4NoHyphen() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
