package com.psicosus.medicalrecord.dto;

import java.util.List;
import java.util.UUID;

public record InternshipHoursSummaryResponse(
        UUID studentId,
        long totalMinutes,
        double totalHours,
        List<InternshipHoursRecord> records
) {
}
