package com.psicosus.auth.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives a 256-bit HMAC key from JWT_SECRET via SHA-256, so any secret length
 * is safe for HS256 regardless of how JWT_SECRET is configured.
 */
@Component
public class JwtKeyProvider {

    private final SecretKey key;

    public JwtKeyProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(sha256(secret));
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
