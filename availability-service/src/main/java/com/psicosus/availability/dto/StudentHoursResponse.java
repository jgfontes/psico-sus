package com.psicosus.availability.dto;

import java.math.BigDecimal;

public record StudentHoursResponse(BigDecimal completedHours, BigDecimal targetHours, double completionPercent) {
}
