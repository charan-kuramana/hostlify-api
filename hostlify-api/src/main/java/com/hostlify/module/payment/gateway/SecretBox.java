package com.hostlify.module.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts owners' gateway secrets at rest (AES-256-GCM). The key comes from GATEWAY_ENCRYPTION_KEY;
 * without it the JWT secret is used, so set a dedicated key before go-live: changing the key later
 * means owners must re-enter their gateway secrets.
 */
@Component
public class SecretBox {

    private static final Logger log = LoggerFactory.getLogger(SecretBox.class);
    private static final String PREFIX = "v1:";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${app.gateway-encryption-key:}") String configured,
                     @Value("${app.jwt.secret}") String jwtSecret) {
        String material = configured;
        if (material == null || material.isBlank()) {
            log.warn("GATEWAY_ENCRYPTION_KEY is not set; owner gateway secrets are encrypted with a key derived from JWT_SECRET");
            material = "gateway:" + jwtSecret;
        }
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) throw new IllegalStateException("Unknown secret format");
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt the saved gateway secret (encryption key changed?)", e);
        }
    }

    public static String mask(String secret) {
        if (secret == null || secret.isEmpty()) return null;
        return "••••" + (secret.length() > 4 ? secret.substring(secret.length() - 4) : "");
    }
}
