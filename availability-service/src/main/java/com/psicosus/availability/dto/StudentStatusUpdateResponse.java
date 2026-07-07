package com.psicosus.availability.dto;

import java.time.Instant;
import java.util.UUID;

public record StudentStatusUpdateResponse(UUID studentId, String status, Instant updatedAt) {
}
