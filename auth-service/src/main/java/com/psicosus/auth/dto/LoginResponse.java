package com.psicosus.auth.dto;

import com.psicosus.auth.entity.Role;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, Role role) {
}
