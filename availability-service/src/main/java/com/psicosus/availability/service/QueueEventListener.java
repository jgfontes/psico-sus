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

    public QueueEventListener(StudentStatusRepository statusRepository,
                               SessionServiceClient sessionServiceClient,
                               StudentService studentService) {
        this.statusRepository = statusRepository;
        this.sessionServiceClient = sessionServiceClient;
        this.studentService = studentService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PATIENT_JOINED)
    public void onPatientJoinedQueue(PatientJoinedQueueEvent event) {
        boolean anyAvailable = statusRepository.findLatestStatusPerStudent().stream()
                .anyMatch(s -> s.getStatus() == StudentStatusValue.AVAILABLE);

        if (!anyAvailable) {
            log.info("No student available for queueEntryId={}; patient stays WAITING", event.queueEntryId());
            return;
        }

        sessionServiceClient.startSession(event.patientId(), event.queueEntryId());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SESSION_ENDED)
    public void onSessionEnded(SessionEndedEvent event) {
        studentService.markAvailableAndAddHours(event.studentId(), event.durationMinutes());
    }
}
