package com.psicosus.availability.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentRegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "cpf must have 11 digits") String cpf,
        @NotNull UUID universityId,
        @NotNull @Positive Integer semester,
        @NotNull @PositiveOrZero BigDecimal targetHours,
        String supervisorCrp
) {
}
