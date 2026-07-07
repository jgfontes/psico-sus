package com.psicosus.auth.repository;

import com.psicosus.auth.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByCpfAndActiveTrue(String cpf);

    boolean existsByCpf(String cpf);
}
