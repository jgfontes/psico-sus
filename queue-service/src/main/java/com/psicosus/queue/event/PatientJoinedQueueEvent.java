package com.psicosus.queue.event;

import java.util.UUID;

public record PatientJoinedQueueEvent(
        UUID queueEntryId,
        UUID patientId,
        String patientName,
        String symptomsDescription
) {
}
