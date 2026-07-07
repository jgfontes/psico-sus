package com.psicosus.auth.dto;

import com.psicosus.auth.entity.Role;

import java.util.UUID;

public record RegisterResponse(UUID userId, Role role) {
}
