package com.psicosus.queue.client;

import com.psicosus.queue.security.InternalTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AvailabilityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityServiceClient.class);

    private final RestClient restClient;
    private final InternalTokenService internalTokenService;

    public AvailabilityServiceClient(@Value("${psicosus.services.availability-service-url}") String baseUrl,
                                      InternalTokenService internalTokenService) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalTokenService = internalTokenService;
    }

    /**
     * How many students are currently working (AVAILABLE or IN_SESSION). Used only to refine the
     * wait-time estimate, so a failure here must never break joining the queue: on any error we
     * return 0 and let the caller fall back to a single-server estimate.
     */
    public long activeStudentCount() {
        try {
            ActiveStudentCount response = restClient.get()
                    .uri("/availability/student/active-count")
                    .header("Authorization", "Bearer " + internalTokenService.issue())
                    .retrieve()
                    .body(ActiveStudentCount.class);
            return response != null ? response.activeStudents() : 0;
        } catch (RestClientException e) {
            log.warn("Could not fetch active student count for wait estimate; falling back. cause={}", e.toString());
            return 0;
        }
    }

    private record ActiveStudentCount(long activeStudents) {
    }
}
