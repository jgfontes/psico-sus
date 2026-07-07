package com.psicosus.supervision.dto;

import java.time.Instant;
import java.util.UUID;

public record ActiveSessionResponse(
        UUID sessionId,
        UUID studentId,
        String studentName,
        String jitsiLink,
        Instant startedAt,
        long currentDurationMinutes
) {
}
