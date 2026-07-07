package com.psicosus.session.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionDetailResponse(
        UUID sessionId,
        String jitsiLink,
        UUID patientId,
        UUID studentId,
        UUID supervisorId,
        String status,
        Instant startedAt
) {
}
