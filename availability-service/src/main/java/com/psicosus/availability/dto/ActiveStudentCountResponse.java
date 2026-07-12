package com.psicosus.availability.dto;

/**
 * Number of students currently "working" — i.e. whose latest status is AVAILABLE or IN_SESSION.
 * Used by queue-service to estimate wait time (patients are served concurrently by these students).
 */
public record ActiveStudentCountResponse(long activeStudents) {
}
