package com.psicosus.session.security;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

/**
 * Mints short-lived SERVICE-role tokens for machine-to-machine calls made outside a user
 * request context, signed with the same shared secret every service trusts.
 */
@Service
public class InternalTokenService {

    private static final String SERVICE_ROLE = "SERVICE";
    private static final long TTL_SECONDS = 60;
    private static final String SERVICE_NAME = "session-service";

    private final JwtKeyProvider keyProvider;

    public InternalTokenService(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public String issue() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(SERVICE_NAME)
                .subject(SERVICE_NAME)
                .claim("role", SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TTL_SECONDS)))
                .signWith(keyProvider.getKey())
                .compact();
    }
}
