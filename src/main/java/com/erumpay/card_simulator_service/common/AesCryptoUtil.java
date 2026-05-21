package com.erumpay.card_simulator_service.common;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AesCryptoUtil {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final int KEY_LENGTH_BYTES = 32;

    @Value("${aes.secret-key}")
    private String secretKey;

    private SecretKeySpec keySpec;

    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("aes.secret-key must decode to exactly 32 bytes");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.secretKey = null;
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 암호화 실패", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 복호화 실패", e);
        }
    }
}
