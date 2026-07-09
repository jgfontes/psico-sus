package com.psicosus.auth.repository;

import com.psicosus.auth.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByEmailAndActiveTrue(String email);

    boolean existsByCpf(String cpf);

    boolean existsByEmail(String email);
}
