package com.erumpay.card_simulator_service.common;

import java.security.SecureRandom;
import java.util.UUID;

// 랜덤 문자열 생성 유틸. 멱등성 키 RANDOM 부분(hex), 빌링키/카드사 토큰(UUID v4 32자), 승인번호(hex 8자) 등에 사용.
public class RandomStringGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private RandomStringGenerator() {}

    public static String generateHex(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = HEX_CHARS[RANDOM.nextInt(16)];
        }
        return new String(result);
    }

    public static String generateUuidV4NoHyphen() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
