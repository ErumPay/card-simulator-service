package com.erumpay.card_simulator_service.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHashUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_LENGTH_BYTES = 16;

    private PasswordHashUtil() {}

    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String plaintext, String saltBase64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(saltBase64));
            byte[] hashed = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 해시 실패", e);
        }
    }

    public static boolean verify(String plaintext, String saltBase64, String storedHashBase64) {
        byte[] computed = Base64.getDecoder().decode(hash(plaintext, saltBase64));
        byte[] stored = Base64.getDecoder().decode(storedHashBase64);
        return MessageDigest.isEqual(computed, stored);
    }
}
