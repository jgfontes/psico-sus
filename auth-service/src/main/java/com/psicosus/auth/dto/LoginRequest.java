package com.psicosus.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String cpf,
        @NotBlank String password
) {
}
