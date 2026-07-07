package com.psicosus.supervision.dto;

import java.util.UUID;

public record SupervisorRegisterResponse(UUID supervisorId, String name, String crp) {
}
