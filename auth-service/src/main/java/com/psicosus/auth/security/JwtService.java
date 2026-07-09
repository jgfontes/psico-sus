package com.psicosus.auth.security;

import com.psicosus.auth.entity.Role;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtKeyProvider keyProvider;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(JwtKeyProvider keyProvider,
                       @Value("${jwt.issuer}") String issuer,
                       @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        this.keyProvider = keyProvider;
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public IssuedToken issueForUser(UUID userId, Role role, UUID referenceId) {
        return issue(userId, role, referenceId, null, expirationSeconds);
    }

    public IssuedToken issueForPatient(UUID patientId, String patientName) {
        return issue(patientId, Role.PATIENT, null, patientName, expirationSeconds);
    }

    private IssuedToken issue(UUID subject, Role role, UUID referenceId, String name, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);

        JwtBuilder builder = Jwts.builder()
                .issuer(issuer)
                .subject(subject.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(keyProvider.getKey());

        if (referenceId != null) {
            builder.claim("referenceId", referenceId.toString());
        }
        if (name != null) {
            builder.claim("name", name);
        }

        return new IssuedToken(builder.compact(), ttlSeconds);
    }

    public record IssuedToken(String accessToken, long expiresIn) {
    }
}
