package com.psicosus.supervision.dto;

import java.time.Instant;
import java.util.UUID;

public record LeaveResponse(UUID interventionId, Instant leftAt) {
}
