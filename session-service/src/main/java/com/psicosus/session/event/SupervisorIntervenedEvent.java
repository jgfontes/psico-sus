package com.psicosus.session.event;

import java.time.Instant;
import java.util.UUID;

public record SupervisorIntervenedEvent(
        UUID interventionId,
        UUID sessionId,
        UUID supervisorId,
        Instant joinedAt
) {
}
