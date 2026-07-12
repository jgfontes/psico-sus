package com.psicosus.queue.security;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

/**
 * Mints short-lived SERVICE-role tokens for machine-to-machine calls (e.g. asking
 * availability-service how many students are working), signed with the same shared secret
 * every service trusts. There is no separate client-credentials flow for this first version.
 */
@Service
public class InternalTokenService {

    private static final String SERVICE_ROLE = "SERVICE";
    private static final long TTL_SECONDS = 60;
    private static final String SERVICE_NAME = "queue-service";

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
