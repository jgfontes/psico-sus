package com.psicosus.medicalrecord.repository;

import com.psicosus.medicalrecord.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    List<MedicalRecord> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
