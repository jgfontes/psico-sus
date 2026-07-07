package com.psicosus.supervision.dto;

import java.util.UUID;

public record SupervisorLookupResponse(UUID supervisorId, String name, String crp) {
}
