package com.psicosus.session.repository;

import com.psicosus.session.entity.Session;
import com.psicosus.session.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findByStatus(SessionStatus status);

    Optional<Session> findByQueueEntryId(UUID queueEntryId);

    Optional<Session> findByPatientIdAndStatusIn(UUID patientId, List<SessionStatus> statuses);
}
