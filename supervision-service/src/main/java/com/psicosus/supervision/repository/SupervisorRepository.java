package com.psicosus.supervision.repository;

import com.psicosus.supervision.entity.Supervisor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupervisorRepository extends JpaRepository<Supervisor, UUID> {

    Optional<Supervisor> findByCrp(String crp);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    boolean existsByCrp(String crp);
}
