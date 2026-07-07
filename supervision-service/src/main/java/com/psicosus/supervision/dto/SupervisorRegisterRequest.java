package com.psicosus.supervision.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record SupervisorRegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "cpf must have 11 digits") String cpf,
        @NotBlank String crp,
        @NotNull UUID universityId
) {
}
