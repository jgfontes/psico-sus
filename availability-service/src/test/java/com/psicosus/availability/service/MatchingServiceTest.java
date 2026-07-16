package com.psicosus.availability.service;

import com.psicosus.availability.client.QueueServiceClient;
import com.psicosus.availability.client.SessionServiceClient;
import com.psicosus.availability.entity.StudentStatus;
import com.psicosus.availability.entity.StudentStatusValue;
import com.psicosus.availability.repository.StudentStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private StudentStatusRepository statusRepository;
    @Mock
    private SessionServiceClient sessionServiceClient;
    @Mock
    private QueueServiceClient queueServiceClient;

    @InjectMocks
    private MatchingService matchingService;

    @Test
    @DisplayName("matchNextWaitingPatient: matches oldest waiting patient when student available")
    void matchNextWaitingPatient_happyPath() {
        UUID queueEntryId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        StudentStatus available = StudentStatus.builder()
                .studentId(UUID.randomUUID())
                .status(StudentStatusValue.AVAILABLE)
                .updatedAt(Instant.now())
                .build();

        when(statusRepository.findLatestStatusPerStudent()).thenReturn(List.of(available));
        when(queueServiceClient.fetchWaitingPatients()).thenReturn(
                List.of(new QueueServiceClient.WaitingPatient(queueEntryId, patientId, Instant.now())));

        matchingService.matchNextWaitingPatient();

        verify(sessionServiceClient).startSession(patientId, queueEntryId);
    }

    @Test
    @DisplayName("matchNextWaitingPatient: does nothing when no student available")
    void matchNextWaitingPatient_noStudent() {
        StudentStatus offline = StudentStatus.builder()
                .studentId(UUID.randomUUID())
                .status(StudentStatusValue.OFFLINE)
                .updatedAt(Instant.now())
                .build();

        when(statusRepository.findLatestStatusPerStudent()).thenReturn(List.of(offline));

        matchingService.matchNextWaitingPatient();

        verify(queueServiceClient, never()).fetchWaitingPatients();
        verify(sessionServiceClient, never()).startSession(any(), any());
    }

    @Test
    @DisplayName("matchNextWaitingPatient: does nothing when no patient waiting")
    void matchNextWaitingPatient_noPatient() {
        StudentStatus available = StudentStatus.builder()
                .studentId(UUID.randomUUID())
                .status(StudentStatusValue.AVAILABLE)
                .updatedAt(Instant.now())
                .build();

        when(statusRepository.findLatestStatusPerStudent()).thenReturn(List.of(available));
        when(queueServiceClient.fetchWaitingPatients()).thenReturn(Collections.emptyList());

        matchingService.matchNextWaitingPatient();

        verify(sessionServiceClient, never()).startSession(any(), any());
    }

    @Test
    @DisplayName("matchNextWaitingPatient: catches exception from session start and doesn't propagate")
    void matchNextWaitingPatient_sessionStartFails() {
        UUID queueEntryId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        StudentStatus available = StudentStatus.builder()
                .studentId(UUID.randomUUID())
                .status(StudentStatusValue.AVAILABLE)
                .updatedAt(Instant.now())
                .build();

        when(statusRepository.findLatestStatusPerStudent()).thenReturn(List.of(available));
        when(queueServiceClient.fetchWaitingPatients()).thenReturn(
                List.of(new QueueServiceClient.WaitingPatient(queueEntryId, patientId, Instant.now())));
        doThrow(new RuntimeException("service unavailable"))
                .when(sessionServiceClient).startSession(patientId, queueEntryId);

        // Should not throw
        matchingService.matchNextWaitingPatient();
    }
}
