package com.psicosus.medicalrecord.dto;

import java.util.UUID;

public record ValidateHoursResponse(UUID internshipHoursId, boolean validated, UUID validatedBy) {
}
