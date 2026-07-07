package com.psicosus.supervision.event;

import java.time.Instant;
import java.util.UUID;

public record SessionStartedEvent(
        UUID sessionId,
        UUID patientId,
        UUID studentId,
        UUID supervisorId,
        String jitsiLink,
        Instant startedAt
) {
}
