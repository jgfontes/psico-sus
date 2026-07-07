package com.psicosus.supervision.repository;

import com.psicosus.supervision.entity.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterventionRepository extends JpaRepository<Intervention, UUID> {
}
