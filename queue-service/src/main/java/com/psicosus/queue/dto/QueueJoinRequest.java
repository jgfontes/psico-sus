package com.psicosus.queue.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * No patientId here on purpose: it is read from the Bearer token's subject claim,
 * never trusted from the request body, so a patient cannot join the queue as someone else.
 */
public record QueueJoinRequest(
        @NotBlank String patientName,
        String symptomsDescription
) {
}
