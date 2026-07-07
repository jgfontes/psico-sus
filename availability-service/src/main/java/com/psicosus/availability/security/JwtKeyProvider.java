package com.psicosus.availability.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives the same 256-bit HMAC key auth-service uses, from the same JWT_SECRET env var.
 * Used both to validate incoming Bearer tokens and to sign short-lived internal SERVICE tokens.
 */
@Component
public class JwtKeyProvider {

    private final SecretKey key;

    public JwtKeyProvider(@Value("${jwt.secret}") String secret) {
        this.key = new SecretKeySpec(sha256(secret), "HmacSHA256");
    }

    public SecretKey getKey() {
        return key;
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
