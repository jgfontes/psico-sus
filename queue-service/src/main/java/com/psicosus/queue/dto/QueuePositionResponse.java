package com.psicosus.queue.dto;

import java.util.UUID;

public record QueuePositionResponse(int position, int estimatedWaitMinutes, String status, UUID sessionId, String jitsiLink) {
}
