package com.psicosus.availability.service;

import com.psicosus.availability.client.SessionServiceClient;
import com.psicosus.availability.config.RabbitMQConfig;
import com.psicosus.availability.entity.StudentStatusValue;
import com.psicosus.availability.event.PatientJoinedQueueEvent;
import com.psicosus.availability.event.SessionEndedEvent;
import com.psicosus.availability.repository.StudentStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class QueueEventListener {

    private static final Logger log = LoggerFactory.getLogger(QueueEventListener.class);

    private final StudentStatusRepository statusRepository;
    private final SessionServiceClient sessionServiceClient;
    private final StudentService studentService;
    private final MatchingService matchingService;

    public QueueEventListener(StudentStatusRepository statusRepository,
                               SessionServiceClient sessionServiceClient,
                               StudentService studentService,
                               MatchingService matchingService) {
        this.statusRepository = statusRepository;
        this.sessionServiceClient = sessionServiceClient;
        this.studentService = studentService;
        this.matchingService = matchingService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PATIENT_JOINED)
    public void onPatientJoinedQueue(PatientJoinedQueueEvent event) {
        boolean anyAvailable = statusRepository.findLatestStatusPerStudent().stream()
                .anyMatch(s -> s.getStatus() == StudentStatusValue.AVAILABLE);

        if (!anyAvailable) {
            log.info("No student available for queueEntryId={}; patient stays WAITING", event.queueEntryId());
            return;
        }

        try {
            sessionServiceClient.startSession(event.patientId(), event.queueEntryId());
        } catch (Exception e) {
            log.warn("Failed to start session for queueEntryId={}: {}", event.queueEntryId(), e.getMessage());
            // ACK the message — the patient stays WAITING and will be picked up by
            // matchNextWaitingPatient when a student becomes available again.
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SESSION_ENDED)
    public void onSessionEnded(SessionEndedEvent event) {
        studentService.markAvailableAndAddHours(event.studentId(), event.durationMinutes());
        matchingService.matchNextWaitingPatient();
    }
}
