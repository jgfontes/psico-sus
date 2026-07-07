package com.psicosus.availability.dto;

import java.util.UUID;

public record StudentRegisterResponse(UUID studentId, String name, String status) {
}
