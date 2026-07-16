package com.psicosus.queue.service;

import com.psicosus.queue.client.AvailabilityServiceClient;
import com.psicosus.queue.dto.QueueJoinRequest;
import com.psicosus.queue.dto.QueueJoinResponse;
import com.psicosus.queue.dto.QueuePositionResponse;
import com.psicosus.queue.entity.QueueEntry;
import com.psicosus.queue.entity.QueueStatus;
import com.psicosus.queue.event.PatientJoinedQueueEvent;
import com.psicosus.queue.repository.QueueEntryRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class QueueService {

    private static final int AVG_SESSION_MINUTES = 5;
    private static final String ROUTING_KEY_PATIENT_JOINED = "patient.joined.queue";

    private final QueueEntryRepository repository;
    private final AvailabilityServiceClient availabilityServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final int timeoutMinutes;

    public QueueService(QueueEntryRepository repository,
                         AvailabilityServiceClient availabilityServiceClient,
                         RabbitTemplate rabbitTemplate,
                         @Value("${psicosus.events.exchange}") String exchangeName,
                         @Value("${psicosus.queue.timeout-minutes}") int timeoutMinutes) {
        this.repository = repository;
        this.availabilityServiceClient = availabilityServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.timeoutMinutes = timeoutMinutes;
    }

    private int estimatedWaitMinutes(int position) {
        long activeStudents = availabilityServiceClient.activeStudentCount();
        long divisor = Math.max(activeStudents, 1);
        int waves = (int) Math.ceil((double) position / divisor);
        return waves * AVG_SESSION_MINUTES;
    }

    @Transactional
    public QueueJoinResponse join(UUID patientId, QueueJoinRequest request) {
        if (repository.existsByPatientIdAndStatus(patientId, QueueStatus.WAITING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "patient already has an active queue entry");
        }

        long waitingAhead = repository.countByStatus(QueueStatus.WAITING);
        int position = (int) waitingAhead + 1;
        int estimatedWait = estimatedWaitMinutes(position);

        QueueEntry entry = QueueEntry.builder()
                .patientId(patientId)
                .status(QueueStatus.WAITING)
                .position(position)
                .estimatedWaitMin(estimatedWait)
                .build();
        entry = repository.save(entry);

        rabbitTemplate.convertAndSend(exchangeName, ROUTING_KEY_PATIENT_JOINED,
                new PatientJoinedQueueEvent(entry.getId(), patientId, request.patientName(), request.symptomsDescription()));

        return new QueueJoinResponse(entry.getId(), position, estimatedWait, entry.getStatus().name());
    }

    @Transactional(readOnly = true)
    public QueuePositionResponse position(UUID patientId) {
        QueueEntry entry = repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.WAITING)
                .or(() -> repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.IN_PROGRESS))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no active queue entry for this patient"));

        if (entry.getStatus() == QueueStatus.IN_PROGRESS) {
            return new QueuePositionResponse(0, 0, entry.getStatus().name(), entry.getSessionId(), entry.getJitsiLink());
        }

        int position = (int) repository.countByStatusAndCreatedAtBefore(QueueStatus.WAITING, entry.getCreatedAt()) + 1;
        int estimatedWait = estimatedWaitMinutes(position);
        return new QueuePositionResponse(position, estimatedWait, entry.getStatus().name(), entry.getSessionId(), entry.getJitsiLink());
    }

    @Transactional
    public void leave(UUID patientId) {
        QueueEntry entry = repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.WAITING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no active queue entry for this patient"));

        entry.setStatus(QueueStatus.CANCELLED);
        entry.setUpdatedAt(Instant.now());
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public long size() {
        return repository.countByStatus(QueueStatus.WAITING);
    }

    @Transactional(readOnly = true)
    public List<QueueEntry> waitingEntries() {
        return repository.findByStatusOrderByCreatedAtAsc(QueueStatus.WAITING);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleEntries() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<QueueEntry> stale = repository.findByStatusAndCreatedAtBefore(QueueStatus.WAITING, cutoff);
        for (QueueEntry entry : stale) {
            entry.setStatus(QueueStatus.EXPIRED);
            entry.setExpiredAt(Instant.now());
        }
        repository.saveAll(stale);
    }
}
