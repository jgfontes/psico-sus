package com.psicosus.medicalrecord.dto;

import java.time.LocalDate;
import java.util.UUID;

public record InternshipHoursRecord(UUID sessionId, int durationMinutes, LocalDate sessionDate, boolean validated) {
}
