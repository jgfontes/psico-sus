package com.psicosus.supervision.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Forwards the caller's own Authorization header instead of minting an internal SERVICE
 * token: every call here happens synchronously inside a real supervisor's request, so the
 * supervisor's own Bearer token is the correct credential to present downstream.
 */
@Component
public class SessionServiceClient {

    private final RestClient restClient;

    public SessionServiceClient(@Value("${psicosus.services.session-service-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public SessionDetail getSession(String authorizationHeader, UUID sessionId) {
        return restClient.get()
                .uri("/session/{sessionId}", sessionId)
                .header("Authorization", authorizationHeader)
                .retrieve()
                .body(SessionDetail.class);
    }

    public List<ActiveSession> getActiveSessions(String authorizationHeader) {
        return restClient.get()
                .uri("/session/active")
                .header("Authorization", authorizationHeader)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ActiveSession>>() {
                });
    }

    public record SessionDetail(UUID sessionId, String jitsiLink, UUID patientId, UUID studentId,
                                 UUID supervisorId, String status, Instant startedAt) {
    }

    public record ActiveSession(UUID sessionId, UUID studentId, String studentName, UUID patientId,
                                 UUID supervisorId, String jitsiLink, Instant startedAt, long currentDurationMinutes) {
    }
}
