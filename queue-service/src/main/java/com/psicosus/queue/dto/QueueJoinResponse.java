package com.psicosus.queue.dto;

import java.util.UUID;

public record QueueJoinResponse(UUID queueEntryId, int position, int estimatedWaitMinutes, String status) {
}
