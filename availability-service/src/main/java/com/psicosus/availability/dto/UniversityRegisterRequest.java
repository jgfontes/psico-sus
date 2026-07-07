package com.psicosus.availability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UniversityRegisterRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{14}", message = "cnpj must have 14 digits") String cnpj,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "state must be a 2-letter UF code") String state,
        @NotBlank String city
) {
}
