package com.psicosus.availability.service;

import com.psicosus.availability.client.QueueServiceClient;
import com.psicosus.availability.client.SessionServiceClient;
import com.psicosus.availability.entity.StudentStatusValue;
import com.psicosus.availability.repository.StudentStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final StudentStatusRepository statusRepository;
    private final SessionServiceClient sessionServiceClient;
    private final QueueServiceClient queueServiceClient;

    public MatchingService(StudentStatusRepository statusRepository,
                           SessionServiceClient sessionServiceClient,
                           QueueServiceClient queueServiceClient) {
        this.statusRepository = statusRepository;
        this.sessionServiceClient = sessionServiceClient;
        this.queueServiceClient = queueServiceClient;
    }

    public void matchNextWaitingPatient() {
        boolean anyAvailable = statusRepository.findLatestStatusPerStudent().stream()
                .anyMatch(s -> s.getStatus() == StudentStatusValue.AVAILABLE);

        if (!anyAvailable) {
            return;
        }

        var waiting = queueServiceClient.fetchWaitingPatients();
        if (waiting.isEmpty()) {
            return;
        }

        var oldest = waiting.get(0);
        log.info("Matching waiting patient queueEntryId={} with available student", oldest.queueEntryId());
        try {
            sessionServiceClient.startSession(oldest.patientId(), oldest.queueEntryId());
        } catch (Exception e) {
            log.warn("Failed to start session for queueEntryId={}: {}", oldest.queueEntryId(), e.getMessage());
        }
    }
}
