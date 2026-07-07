package com.psicosus.availability.client;

import com.psicosus.availability.security.InternalTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * availability.student only stores the responsible supervisor's CRP (a license number, not a
 * UUID) — supervisor identity lives in supervision-service's own schema. This client resolves
 * CRP -> supervisorId via a small lookup endpoint added to supervision-service for that reason
 * (see supervision-service's SupervisorController).
 */
@Component
public class SupervisionServiceClient {

    private final RestClient restClient;
    private final InternalTokenService internalTokenService;

    public SupervisionServiceClient(@Value("${psicosus.services.supervision-service-url}") String baseUrl,
                                     InternalTokenService internalTokenService) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalTokenService = internalTokenService;
    }

    public UUID resolveSupervisorIdByCrp(String crp) {
        SupervisorLookupResponse response = restClient.get()
                .uri("/supervision/supervisor/by-crp/{crp}", crp)
                .header("Authorization", "Bearer " + internalTokenService.issue())
                .retrieve()
                .body(SupervisorLookupResponse.class);
        return response != null ? response.supervisorId() : null;
    }

    private record SupervisorLookupResponse(UUID supervisorId, String name, String crp) {
    }
}
