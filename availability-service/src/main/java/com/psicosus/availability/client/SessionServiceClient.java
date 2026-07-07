package com.psicosus.availability.client;

import com.psicosus.availability.security.InternalTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class SessionServiceClient {

    private final RestClient restClient;
    private final InternalTokenService internalTokenService;

    public SessionServiceClient(@Value("${psicosus.services.session-service-url}") String baseUrl,
                                 InternalTokenService internalTokenService) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalTokenService = internalTokenService;
    }

    public void startSession(UUID patientId, UUID queueEntryId) {
        restClient.post()
                .uri("/session/start")
                .header("Authorization", "Bearer " + internalTokenService.issue())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StartSessionRequest(patientId, queueEntryId))
                .retrieve()
                .toBodilessEntity();
    }

    private record StartSessionRequest(UUID patientId, UUID queueEntryId) {
    }
}
