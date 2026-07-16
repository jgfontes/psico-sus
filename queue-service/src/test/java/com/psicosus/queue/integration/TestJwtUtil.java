package com.psicosus.queue.integration;

import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Test utility that mints JWT tokens signed with HS256, using the same key derivation
 * as JwtKeyProvider (SHA-256 hash of the plain secret).
 */
public final class TestJwtUtil {

    private static final String TEST_SECRET = "test-secret";
    private static final SecretKey KEY = deriveKey(TEST_SECRET);

    private TestJwtUtil() {
    }

    public static String patientToken(UUID patientId) {
        return buildToken(patientId.toString(), "PATIENT");
    }

    public static String serviceToken() {
        return buildToken("queue-service", "SERVICE");
    }

    private static String buildToken(String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("test")
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(KEY)
                .compact();
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
