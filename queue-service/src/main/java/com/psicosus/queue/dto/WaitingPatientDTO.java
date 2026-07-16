package com.psicosus.queue.dto;

import java.time.Instant;
import java.util.UUID;

public record WaitingPatientDTO(UUID queueEntryId, UUID patientId, Instant createdAt) {
}
