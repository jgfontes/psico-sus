package com.psicosus.availability.client;

import com.psicosus.availability.security.InternalTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class QueueServiceClient {

    private static final Logger log = LoggerFactory.getLogger(QueueServiceClient.class);

    private final RestClient restClient;
    private final InternalTokenService internalTokenService;

    public QueueServiceClient(@Value("${psicosus.services.queue-service-url}") String baseUrl,
                               InternalTokenService internalTokenService) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalTokenService = internalTokenService;
    }

    public List<WaitingPatient> fetchWaitingPatients() {
        try {
            List<WaitingPatient> result = restClient.get()
                    .uri("/queue/waiting")
                    .header("Authorization", "Bearer " + internalTokenService.issue())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result != null ? result : Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Could not fetch waiting patients from queue-service: {}", e.toString());
            return Collections.emptyList();
        }
    }

    public record WaitingPatient(UUID queueEntryId, UUID patientId, Instant createdAt) {
    }
}
