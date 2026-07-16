package com.psicosus.queue.service;

import com.psicosus.queue.client.AvailabilityServiceClient;
import com.psicosus.queue.dto.QueueJoinRequest;
import com.psicosus.queue.dto.QueueJoinResponse;
import com.psicosus.queue.dto.QueuePositionResponse;
import com.psicosus.queue.entity.QueueEntry;
import com.psicosus.queue.entity.QueueStatus;
import com.psicosus.queue.event.PatientJoinedQueueEvent;
import com.psicosus.queue.repository.QueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueEntryRepository repository;
    @Mock
    private AvailabilityServiceClient availabilityServiceClient;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private QueueService queueService;

    private UUID patientId;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(repository, availabilityServiceClient, rabbitTemplate,
                "psicosus.events", 30);
        patientId = UUID.randomUUID();
    }

    @Test
    @DisplayName("join: creates entry and publishes event")
    void join_happyPath() {
        when(repository.existsByPatientIdAndStatus(patientId, QueueStatus.WAITING)).thenReturn(false);
        when(repository.countByStatus(QueueStatus.WAITING)).thenReturn(2L);
        when(availabilityServiceClient.activeStudentCount()).thenReturn(1L);
        when(repository.save(any(QueueEntry.class))).thenAnswer(inv -> {
            QueueEntry e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        QueueJoinRequest request = new QueueJoinRequest("Ana", "anxiety");
        QueueJoinResponse response = queueService.join(patientId, request);

        assertThat(response.position()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("WAITING");

        verify(rabbitTemplate).convertAndSend(eq("psicosus.events"),
                eq("patient.joined.queue"), any(PatientJoinedQueueEvent.class));
    }

    @Test
    @DisplayName("join: rejects duplicate — patient already WAITING")
    void join_duplicateRejected() {
        when(repository.existsByPatientIdAndStatus(patientId, QueueStatus.WAITING)).thenReturn(true);

        QueueJoinRequest request = new QueueJoinRequest("Ana", "anxiety");

        assertThatThrownBy(() -> queueService.join(patientId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already has an active queue entry");

        verify(repository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
    }

    @Test
    @DisplayName("position: returns session info when IN_PROGRESS")
    void position_inProgress() {
        UUID sessionId = UUID.randomUUID();
        String jitsiLink = "https://meet.jit.si/psicosus-room";

        QueueEntry entry = QueueEntry.builder()
                .id(UUID.randomUUID())
                .patientId(patientId)
                .status(QueueStatus.IN_PROGRESS)
                .sessionId(sessionId)
                .jitsiLink(jitsiLink)
                .createdAt(Instant.now())
                .build();

        when(repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.WAITING))
                .thenReturn(Optional.empty());
        when(repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.IN_PROGRESS))
                .thenReturn(Optional.of(entry));

        QueuePositionResponse response = queueService.position(patientId);

        assertThat(response.position()).isEqualTo(0);
        assertThat(response.estimatedWaitMinutes()).isEqualTo(0);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.jitsiLink()).isEqualTo(jitsiLink);
    }

    @Test
    @DisplayName("position: throws 404 when no active entry")
    void position_notFound() {
        when(repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.WAITING))
                .thenReturn(Optional.empty());
        when(repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.position(patientId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no active queue entry");
    }

    @Test
    @DisplayName("leave: marks entry CANCELLED")
    void leave_happyPath() {
        QueueEntry entry = QueueEntry.builder()
                .id(UUID.randomUUID())
                .patientId(patientId)
                .status(QueueStatus.WAITING)
                .createdAt(Instant.now())
                .build();

        when(repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.WAITING))
                .thenReturn(Optional.of(entry));
        when(repository.save(any(QueueEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        queueService.leave(patientId);

        ArgumentCaptor<QueueEntry> captor = ArgumentCaptor.forClass(QueueEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QueueStatus.CANCELLED);
    }
}
