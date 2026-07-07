package com.psicosus.medicalrecord.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "record", schema = "medical_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "supervisor_id", nullable = false)
    private UUID supervisorId;

    @Column(name = "clinical_summary", nullable = false, columnDefinition = "TEXT")
    private String clinicalSummary;

    @Column(length = 10)
    private String icd10;

    @Column(columnDefinition = "TEXT")
    private String referral;

    @Column(name = "suggested_return")
    private LocalDate suggestedReturn;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
