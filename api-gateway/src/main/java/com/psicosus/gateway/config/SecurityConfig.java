package com.psicosus.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${jwt.secret:local-dev-secret-change-me}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/auth/register", "/auth/login", "/auth/patient-session").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/*/swagger-ui/**", "/*/v3/api-docs/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKey key = signingKey();
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }

    private SecretKey signingKey() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(jwtSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
