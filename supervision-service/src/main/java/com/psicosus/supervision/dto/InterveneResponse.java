package com.psicosus.supervision.dto;

import java.time.Instant;
import java.util.UUID;

public record InterveneResponse(UUID interventionId, String jitsiLink, UUID sessionId, Instant joinedAt) {
}
