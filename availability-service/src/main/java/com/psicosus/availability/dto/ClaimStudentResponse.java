package com.psicosus.availability.dto;

import java.util.UUID;

/**
 * Response returned when a student is atomically claimed for a session.
 * The student is guaranteed to have been AVAILABLE and is now IN_SESSION.
 */
public record ClaimStudentResponse(UUID studentId, String name, UUID supervisorId) {
}
