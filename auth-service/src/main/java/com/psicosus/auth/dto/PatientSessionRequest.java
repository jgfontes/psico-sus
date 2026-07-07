package com.psicosus.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PatientSessionRequest(@NotBlank String patientName) {
}
