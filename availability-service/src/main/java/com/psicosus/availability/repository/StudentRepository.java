package com.psicosus.availability.repository;

import com.psicosus.availability.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);
}
