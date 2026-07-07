package com.psicosus.auth.dto;

import com.psicosus.auth.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "cpf must have 11 digits") String cpf,
        @NotBlank String password,
        @NotNull Role role,
        UUID referenceId
) {
}
