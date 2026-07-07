package com.psicosus.availability.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student", schema = "availability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "supervisor_crp", length = 20)
    private String supervisorCrp;

    @Column(name = "university_id", nullable = false)
    private UUID universityId;

    @Column(nullable = false)
    private Integer semester;

    @Column(name = "completed_hours", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal completedHours = BigDecimal.ZERO;

    @Column(name = "target_hours", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal targetHours = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
