package com.psicosus.availability.event;

import java.time.LocalDate;
import java.util.UUID;

public record SessionEndedEvent(
        UUID sessionId,
        UUID patientId,
        UUID studentId,
        UUID supervisorId,
        UUID queueEntryId,
        int durationMinutes,
        LocalDate sessionDate,
        String clinicalSummary,
        String icd10,
        String referral,
        LocalDate suggestedReturn
) {
}
