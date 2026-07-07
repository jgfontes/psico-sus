package com.psicosus.session.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session", schema = "session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    /**
     * Assigned by the application (not @GeneratedValue): the Jitsi room name/link are derived
     * from this id and must be known before the first insert, since jitsi_link/jitsi_room_name
     * are NOT NULL columns — there is no room for a later "generate id, then update" step.
     */
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "supervisor_id", nullable = false)
    private UUID supervisorId;

    @Column(name = "queue_entry_id", nullable = false)
    private UUID queueEntryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SessionStatus status = SessionStatus.WAITING_START;

    @Column(name = "jitsi_link", nullable = false, length = 300)
    private String jitsiLink;

    @Column(name = "jitsi_room_name", nullable = false, length = 200)
    private String jitsiRoomName;

    /**
     * Denormalized snapshot of the student's name at session-start time (captured from
     * availability-service's GET /availability/student/next response), so GET /session/active
     * doesn't need a live lookup for every row.
     */
    @Column(name = "student_name", length = 150)
    private String studentName;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
