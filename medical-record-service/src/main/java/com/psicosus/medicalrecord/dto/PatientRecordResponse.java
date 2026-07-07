package com.psicosus.medicalrecord.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientRecordResponse(
        UUID recordId,
        UUID sessionId,
        String clinicalSummary,
        String icd10,
        String referral,
        LocalDate suggestedReturn,
        Instant createdAt
) {
}
