package com.psicosus.session.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * studentId and supervisorId are not in the original spec's example payload for this endpoint
 * but are included here so supervision-service can both filter this list down to "sessions
 * under this supervisor" and pass studentId through in its own /supervision/active-sessions
 * response (which does list it), without a second round trip per session.
 */
public record ActiveSessionResponse(
        UUID sessionId,
        UUID studentId,
        String studentName,
        UUID patientId,
        UUID supervisorId,
        String jitsiLink,
        Instant startedAt,
        long currentDurationMinutes
) {
}
