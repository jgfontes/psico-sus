package com.psicosus.session.dto;

import java.time.Instant;
import java.util.UUID;

public record EndSessionResponse(UUID sessionId, String status, int durationMinutes, Instant endedAt) {
}
