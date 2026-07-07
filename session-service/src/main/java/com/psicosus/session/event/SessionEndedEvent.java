package com.psicosus.session.event;

import java.time.LocalDate;
import java.util.UUID;

public record SessionEndedEvent(
        UUID sessionId,
        UUID patientId,
        UUID studentId,
        UUID supervisorId,
        int durationMinutes,
        LocalDate sessionDate,
        String clinicalSummary,
        String icd10,
        String referral,
        LocalDate suggestedReturn
) {
}
