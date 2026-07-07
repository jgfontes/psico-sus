package com.psicosus.auth.dto;

import java.util.UUID;

public record PatientSessionResponse(String accessToken, String tokenType, long expiresIn, UUID patientId) {
}
