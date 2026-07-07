package com.psicosus.session.dto;

import java.time.Instant;
import java.util.UUID;

public record ConfirmStartResponse(UUID sessionId, String status, Instant startedAt) {
}
