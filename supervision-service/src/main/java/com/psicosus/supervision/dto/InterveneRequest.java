package com.psicosus.supervision.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * No supervisorId here on purpose: it is read from the Bearer token's referenceId claim,
 * never trusted from the request body.
 */
public record InterveneRequest(@NotBlank String reason) {
}
