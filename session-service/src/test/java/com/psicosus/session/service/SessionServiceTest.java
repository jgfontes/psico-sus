package com.psicosus.session.service;

import com.psicosus.session.client.AvailabilityServiceClient;
import com.psicosus.session.config.RabbitMQConfig;
import com.psicosus.session.dto.StartSessionResponse;
import com.psicosus.session.entity.Session;
import com.psicosus.session.entity.SessionEvent;
import com.psicosus.session.entity.SessionStatus;
import com.psicosus.session.event.SessionStartedEvent;
import com.psicosus.session.repository.SessionEventRepository;
import com.psicosus.session.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private AvailabilityServiceClient availabilityServiceClient;
    @Mock
    private JitsiService jitsiService;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private SessionService sessionService;

    private UUID patientId;
    private UUID queueEntryId;
    private UUID studentId;
    private UUID supervisorId;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, sessionEventRepository,
                availabilityServiceClient, jitsiService, rabbitTemplate, "psicosus.events");
        patientId = UUID.randomUUID();
        queueEntryId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        supervisorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("start: creates new session using atomic claim endpoint")
    void start_createsNewSession() {
        when(sessionRepository.findByQueueEntryId(queueEntryId)).thenReturn(Optional.empty());
        when(availabilityServiceClient.claimNextStudent())
                .thenReturn(new AvailabilityServiceClient.ClaimedStudent(studentId, "João", supervisorId));
        when(jitsiService.generateRoom(any(UUID.class)))
                .thenReturn(new JitsiService.JitsiRoomDTO("psicosus-abc", "https://meet.jit.si/psicosus-abc"));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionEventRepository.save(any(SessionEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        StartSessionResponse response = sessionService.start(patientId, queueEntryId);

        assertThat(response.studentId()).isEqualTo(studentId);
        assertThat(response.supervisorId()).isEqualTo(supervisorId);
        assertThat(response.jitsiLink()).isEqualTo("https://meet.jit.si/psicosus-abc");
        assertThat(response.status()).isEqualTo("WAITING_START");

        // Should NOT call the deprecated markStudentInSession
        verify(availabilityServiceClient, never()).markStudentInSession(any());
        // Should publish event
        verify(rabbitTemplate).convertAndSend(eq("psicosus.events"),
                eq(RabbitMQConfig.ROUTING_KEY_SESSION_STARTED), any(SessionStartedEvent.class));
    }

    @Test
    @DisplayName("start: returns existing session if queueEntryId already has one (idempotent)")
    void start_existingSession_idempotent() {
        Session existing = Session.builder()
                .id(UUID.randomUUID())
                .patientId(patientId)
                .studentId(studentId)
                .supervisorId(supervisorId)
                .queueEntryId(queueEntryId)
                .status(SessionStatus.WAITING_START)
                .jitsiLink("https://meet.jit.si/psicosus-existing")
                .jitsiRoomName("psicosus-existing")
                .createdAt(Instant.now())
                .build();

        when(sessionRepository.findByQueueEntryId(queueEntryId)).thenReturn(Optional.of(existing));

        StartSessionResponse response = sessionService.start(patientId, queueEntryId);

        assertThat(response.sessionId()).isEqualTo(existing.getId());
        assertThat(response.jitsiLink()).isEqualTo("https://meet.jit.si/psicosus-existing");

        // Should not attempt to claim a student or save anything
        verify(availabilityServiceClient, never()).claimNextStudent();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("start: propagates exception when no student available")
    void start_noStudentAvailable() {
        when(sessionRepository.findByQueueEntryId(queueEntryId)).thenReturn(Optional.empty());
        when(availabilityServiceClient.claimNextStudent())
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "no student currently available"));

        assertThatThrownBy(() -> sessionService.start(patientId, queueEntryId))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);

        verify(sessionRepository, never()).save(any());
    }
}
