package com.psicosus.session.repository;

import com.psicosus.session.entity.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionEventRepository extends JpaRepository<SessionEvent, UUID> {
}
