package com.psicosus.medicalrecord.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class JwtClaims {

    private JwtClaims() {
    }

    public static UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }

    public static UUID referenceId(Jwt jwt) {
        String ref = jwt.getClaimAsString("referenceId");
        return ref != null ? UUID.fromString(ref) : null;
    }
}
