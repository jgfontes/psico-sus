package com.psicosus.availability.dto;

import com.psicosus.availability.entity.StudentStatusValue;
import jakarta.validation.constraints.NotNull;

public record StudentStatusUpdateRequest(@NotNull StudentStatusValue status) {
}
