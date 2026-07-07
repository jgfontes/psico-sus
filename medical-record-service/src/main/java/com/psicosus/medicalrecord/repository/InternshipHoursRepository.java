package com.psicosus.medicalrecord.repository;

import com.psicosus.medicalrecord.entity.InternshipHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InternshipHoursRepository extends JpaRepository<InternshipHours, UUID> {

    List<InternshipHours> findByStudentIdOrderBySessionDateDesc(UUID studentId);
}
