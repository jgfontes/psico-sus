package com.psicosus.session.repository;

import com.psicosus.session.entity.Session;
import com.psicosus.session.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findByStatus(SessionStatus status);
}
