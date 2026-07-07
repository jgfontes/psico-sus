package com.psicosus.session.service;

import com.psicosus.session.client.AvailabilityServiceClient;
import com.psicosus.session.config.RabbitMQConfig;
import com.psicosus.session.dto.*;
import com.psicosus.session.entity.Session;
import com.psicosus.session.entity.SessionEvent;
import com.psicosus.session.entity.SessionEventType;
import com.psicosus.session.entity.SessionStatus;
import com.psicosus.session.event.SessionEndedEvent;
import com.psicosus.session.event.SessionStartedEvent;
import com.psicosus.session.repository.SessionEventRepository;
import com.psicosus.session.repository.SessionRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionEventRepository sessionEventRepository;
    private final AvailabilityServiceClient availabilityServiceClient;
    private final JitsiService jitsiService;
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public SessionService(SessionRepository sessionRepository,
                           SessionEventRepository sessionEventRepository,
                           AvailabilityServiceClient availabilityServiceClient,
                           JitsiService jitsiService,
                           RabbitTemplate rabbitTemplate,
                           @Value("${psicosus.events.exchange}") String exchangeName) {
        this.sessionRepository = sessionRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.availabilityServiceClient = availabilityServiceClient;
        this.jitsiService = jitsiService;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    @Transactional
    public StartSessionResponse start(UUID patientId, UUID queueEntryId) {
        AvailabilityServiceClient.NextStudent nextStudent = availabilityServiceClient.fetchNextAvailableStudent();

        UUID sessionId = UUID.randomUUID();
        JitsiService.JitsiRoomDTO room = jitsiService.generateRoom(sessionId);

        Session session = Session.builder()
                .id(sessionId)
                .patientId(patientId)
                .studentId(nextStudent.studentId())
                .supervisorId(nextStudent.supervisorId())
                .queueEntryId(queueEntryId)
                .studentName(nextStudent.name())
                .status(SessionStatus.WAITING_START)
                .jitsiLink(room.jitsiLink())
                .jitsiRoomName(room.roomName())
                .build();
        session = sessionRepository.save(session);

        availabilityServiceClient.markStudentInSession(nextStudent.studentId());

        sessionEventRepository.save(SessionEvent.builder()
                .sessionId(session.getId())
                .type(SessionEventType.STARTED)
                .description("session created, waiting for student to join")
                .build());

        rabbitTemplate.convertAndSend(exchangeName, RabbitMQConfig.ROUTING_KEY_SESSION_STARTED,
                new SessionStartedEvent(session.getId(), patientId, session.getStudentId(),
                        session.getSupervisorId(), session.getJitsiLink(), session.getCreatedAt()));

        return new StartSessionResponse(session.getId(), session.getJitsiLink(), session.getJitsiRoomName(),
                session.getPatientId(), session.getStudentId(), session.getSupervisorId(),
                session.getStatus().name(), session.getCreatedAt());
    }

    @Transactional
    public ConfirmStartResponse confirmStart(UUID sessionId, UUID studentId) {
        Session session = getOwnedByStudent(sessionId, studentId);

        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(Instant.now());
        session = sessionRepository.save(session);

        return new ConfirmStartResponse(session.getId(), session.getStatus().name(), session.getStartedAt());
    }

    @Transactional
    public EndSessionResponse end(UUID sessionId, UUID studentId, EndSessionRequest request) {
        Session session = getOwnedByStudent(sessionId, studentId);

        Instant endedAt = Instant.now();
        Instant referenceStart = session.getStartedAt() != null ? session.getStartedAt() : session.getCreatedAt();
        int durationMinutes = (int) Duration.between(referenceStart, endedAt).toMinutes();

        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(endedAt);
        session.setDurationMinutes(durationMinutes);
        session = sessionRepository.save(session);

        sessionEventRepository.save(SessionEvent.builder()
                .sessionId(session.getId())
                .type(SessionEventType.ENDED)
                .authorId(studentId)
                .description("session ended by student")
                .build());

        rabbitTemplate.convertAndSend(exchangeName, RabbitMQConfig.ROUTING_KEY_SESSION_ENDED,
                new SessionEndedEvent(session.getId(), session.getPatientId(), session.getStudentId(),
                        session.getSupervisorId(), durationMinutes, endedAt.atZone(ZoneOffset.UTC).toLocalDate(),
                        request.clinicalSummary(), request.icd10(), request.referral(), request.suggestedReturn()));

        return new EndSessionResponse(session.getId(), session.getStatus().name(), durationMinutes, endedAt);
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse get(UUID sessionId) {
        Session session = find(sessionId);
        return new SessionDetailResponse(session.getId(), session.getJitsiLink(), session.getPatientId(),
                session.getStudentId(), session.getSupervisorId(), session.getStatus().name(), session.getStartedAt());
    }

    @Transactional(readOnly = true)
    public List<ActiveSessionResponse> active() {
        Instant now = Instant.now();
        return sessionRepository.findByStatus(SessionStatus.IN_PROGRESS).stream()
                .map(s -> new ActiveSessionResponse(
                        s.getId(),
                        s.getStudentId(),
                        s.getStudentName(),
                        s.getPatientId(),
                        s.getSupervisorId(),
                        s.getJitsiLink(),
                        s.getStartedAt(),
                        s.getStartedAt() != null ? Duration.between(s.getStartedAt(), now).toMinutes() : 0))
                .toList();
    }

    @Transactional
    public void recordSupervisorJoined(UUID sessionId, UUID supervisorId) {
        sessionEventRepository.save(SessionEvent.builder()
                .sessionId(sessionId)
                .type(SessionEventType.SUPERVISOR_JOINED)
                .authorId(supervisorId)
                .description("supervisor intervened")
                .build());
    }

    private Session find(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
    }

    private Session getOwnedByStudent(UUID sessionId, UUID studentId) {
        Session session = find(sessionId);
        if (!session.getStudentId().equals(studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot act on another student's session");
        }
        return session;
    }
}
