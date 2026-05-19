package com.erumpay.card_simulator_service.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// AES-256-ECB. 검색 가능한 deterministic 암호화 (같은 평문 → 같은 암호문). WHERE 매칭 용도로 사용.
@Component
public class AesCryptoUtil {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final int KEY_LENGTH_BYTES = 32;

    @Value("${aes.secret-key}")
    private String secretKey;

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildKeySpec());
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
            cipher.init(Cipher.DECRYPT_MODE, buildKeySpec());
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 복호화 실패", e);
        }
    }

    // Base64로 인코딩된 32바이트 키만 허용
    private SecretKeySpec buildKeySpec() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("aes.secret-key must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
