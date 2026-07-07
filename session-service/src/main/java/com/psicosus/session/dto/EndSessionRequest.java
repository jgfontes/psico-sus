package com.psicosus.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EndSessionRequest(
        @NotNull UUID endedBy,
        @NotBlank String clinicalSummary,
        String icd10,
        String referral,
        LocalDate suggestedReturn
) {
}
