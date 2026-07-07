package com.psicosus.session.dto;

import java.time.Instant;
import java.util.UUID;

public record StartSessionResponse(
        UUID sessionId,
        String jitsiLink,
        String roomName,
        UUID patientId,
        UUID studentId,
        UUID supervisorId,
        String status,
        Instant createdAt
) {
}
