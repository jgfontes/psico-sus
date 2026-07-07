package com.psicosus.session.client;

import com.psicosus.session.security.InternalTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class AvailabilityServiceClient {

    private final RestClient restClient;
    private final InternalTokenService internalTokenService;

    public AvailabilityServiceClient(@Value("${psicosus.services.availability-service-url}") String baseUrl,
                                      InternalTokenService internalTokenService) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalTokenService = internalTokenService;
    }

    public NextStudent fetchNextAvailableStudent() {
        return restClient.get()
                .uri("/availability/student/next")
                .header("Authorization", "Bearer " + internalTokenService.issue())
                .retrieve()
                .body(NextStudent.class);
    }

    public void markStudentInSession(UUID studentId) {
        restClient.patch()
                .uri("/availability/student/{studentId}/status", studentId)
                .header("Authorization", "Bearer " + internalTokenService.issue())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StatusUpdate("IN_SESSION"))
                .retrieve()
                .toBodilessEntity();
    }

    public record NextStudent(UUID studentId, String name, UUID supervisorId) {
    }

    private record StatusUpdate(String status) {
    }
}
