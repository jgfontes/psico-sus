package com.psicosus.queue.service;

import com.psicosus.queue.config.RabbitMQConfig;
import com.psicosus.queue.entity.QueueEntry;
import com.psicosus.queue.entity.QueueStatus;
import com.psicosus.queue.event.SessionEndedEvent;
import com.psicosus.queue.event.SessionStartedEvent;
import com.psicosus.queue.repository.QueueEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionEventListenerTest {

    @Mock
    private QueueEntryRepository repository;

    @InjectMocks
    private SessionEventListener listener;

    @Test
    @DisplayName("onSessionStarted: transitions WAITING → IN_PROGRESS with sessionId and jitsiLink")
    void onSessionStarted_happyPath() {
        UUID queueEntryId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String jitsiLink = "https://meet.jit.si/psicosus-room";

        QueueEntry entry = QueueEntry.builder()
                .id(queueEntryId)
                .patientId(UUID.randomUUID())
                .status(QueueStatus.WAITING)
                .createdAt(Instant.now())
                .build();

        when(repository.findById(queueEntryId)).thenReturn(Optional.of(entry));
        when(repository.save(any(QueueEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionStartedEvent event = new SessionStartedEvent(sessionId, entry.getPatientId(),
                UUID.randomUUID(), UUID.randomUUID(), queueEntryId, jitsiLink, Instant.now());

        listener.onSessionStarted(event);

        ArgumentCaptor<QueueEntry> captor = ArgumentCaptor.forClass(QueueEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QueueStatus.IN_PROGRESS);
        assertThat(captor.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(captor.getValue().getJitsiLink()).isEqualTo(jitsiLink);
    }

    @Test
    @DisplayName("onSessionStarted: skips if entry already IN_PROGRESS (idempotent)")
    void onSessionStarted_alreadyInProgress() {
        UUID queueEntryId = UUID.randomUUID();

        QueueEntry entry = QueueEntry.builder()
                .id(queueEntryId)
                .patientId(UUID.randomUUID())
                .status(QueueStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .build();

        when(repository.findById(queueEntryId)).thenReturn(Optional.of(entry));

        SessionStartedEvent event = new SessionStartedEvent(UUID.randomUUID(), entry.getPatientId(),
                UUID.randomUUID(), UUID.randomUUID(), queueEntryId, "link", Instant.now());

        listener.onSessionStarted(event);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("onSessionEnded: transitions IN_PROGRESS → ATTENDED")
    void onSessionEnded_happyPath() {
        UUID queueEntryId = UUID.randomUUID();

        QueueEntry entry = QueueEntry.builder()
                .id(queueEntryId)
                .patientId(UUID.randomUUID())
                .status(QueueStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .build();

        when(repository.findById(queueEntryId)).thenReturn(Optional.of(entry));
        when(repository.save(any(QueueEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionEndedEvent event = new SessionEndedEvent(UUID.randomUUID(), entry.getPatientId(),
                UUID.randomUUID(), UUID.randomUUID(), queueEntryId, 30, LocalDate.now(),
                "summary", "F41.1", null, null);

        listener.onSessionEnded(event);

        ArgumentCaptor<QueueEntry> captor = ArgumentCaptor.forClass(QueueEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QueueStatus.ATTENDED);
    }

    @Test
    @DisplayName("onSessionEnded: skips if entry already ATTENDED (idempotent)")
    void onSessionEnded_alreadyAttended() {
        UUID queueEntryId = UUID.randomUUID();

        QueueEntry entry = QueueEntry.builder()
                .id(queueEntryId)
                .patientId(UUID.randomUUID())
                .status(QueueStatus.ATTENDED)
                .createdAt(Instant.now())
                .build();

        when(repository.findById(queueEntryId)).thenReturn(Optional.of(entry));

        SessionEndedEvent event = new SessionEndedEvent(UUID.randomUUID(), entry.getPatientId(),
                UUID.randomUUID(), UUID.randomUUID(), queueEntryId, 30, LocalDate.now(),
                "summary", "F41.1", null, null);

        listener.onSessionEnded(event);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("onSessionStarted: logs warning when entry not found")
    void onSessionStarted_entryNotFound() {
        UUID queueEntryId = UUID.randomUUID();
        when(repository.findById(queueEntryId)).thenReturn(Optional.empty());

        SessionStartedEvent event = new SessionStartedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), queueEntryId, "link", Instant.now());

        // Should not throw
        listener.onSessionStarted(event);

        verify(repository, never()).save(any());
    }
}
