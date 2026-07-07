package com.psicosus.queue.repository;

import com.psicosus.queue.entity.QueueEntry;
import com.psicosus.queue.entity.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    long countByStatus(QueueStatus status);

    long countByStatusAndCreatedAtBefore(QueueStatus status, Instant createdAt);

    Optional<QueueEntry> findFirstByPatientIdAndStatusOrderByCreatedAtDesc(UUID patientId, QueueStatus status);

    List<QueueEntry> findByStatusAndCreatedAtBefore(QueueStatus status, Instant createdAt);
}
