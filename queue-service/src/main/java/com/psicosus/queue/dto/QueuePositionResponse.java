package com.psicosus.queue.dto;

public record QueuePositionResponse(int position, int estimatedWaitMinutes, String status) {
}
