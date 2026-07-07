package com.psicosus.session.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartSessionRequest(@NotNull UUID patientId, @NotNull UUID queueEntryId) {
}
