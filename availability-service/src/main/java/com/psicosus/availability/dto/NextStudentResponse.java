package com.psicosus.availability.dto;

import java.util.UUID;

public record NextStudentResponse(UUID studentId, String name, UUID supervisorId) {
}
