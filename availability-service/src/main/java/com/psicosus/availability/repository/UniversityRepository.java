package com.psicosus.availability.repository;

import com.psicosus.availability.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UniversityRepository extends JpaRepository<University, UUID> {

    boolean existsByCnpj(String cnpj);
}
