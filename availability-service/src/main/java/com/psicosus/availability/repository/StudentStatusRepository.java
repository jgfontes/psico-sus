package com.psicosus.availability.repository;

import com.psicosus.availability.entity.StudentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentStatusRepository extends JpaRepository<StudentStatus, UUID> {

    Optional<StudentStatus> findFirstByStudentIdOrderByUpdatedAtDesc(UUID studentId);

    @Query(value = """
            SELECT DISTINCT ON (student_id) *
            FROM availability.student_status
            ORDER BY student_id, updated_at DESC
            """, nativeQuery = true)
    List<StudentStatus> findLatestStatusPerStudent();

    /**
     * Returns the oldest AVAILABLE student using FOR UPDATE SKIP LOCKED to prevent
     * concurrent claims from selecting the same student. The subquery finds each
     * student's latest status row; the outer query filters for AVAILABLE and locks.
     */
    @Query(value = """
            SELECT ss.*
            FROM availability.student_status ss
            INNER JOIN (
                SELECT DISTINCT ON (student_id) id
                FROM availability.student_status
                ORDER BY student_id, updated_at DESC
            ) latest ON latest.id = ss.id
            WHERE ss.status = 'AVAILABLE'
            ORDER BY ss.updated_at ASC
            LIMIT 1
            FOR UPDATE OF ss SKIP LOCKED
            """, nativeQuery = true)
    Optional<StudentStatus> findAndLockOldestAvailable();

    /**
     * Finds available students excluding a specific set of student IDs.
     * Used for retry when a claim fails (supervisor resolution issue).
     */
    @Query(value = """
            SELECT ss.*
            FROM availability.student_status ss
            INNER JOIN (
                SELECT DISTINCT ON (student_id) id
                FROM availability.student_status
                ORDER BY student_id, updated_at DESC
            ) latest ON latest.id = ss.id
            WHERE ss.status = 'AVAILABLE'
              AND ss.student_id NOT IN (:excludeIds)
            ORDER BY ss.updated_at ASC
            LIMIT 1
            FOR UPDATE OF ss SKIP LOCKED
            """, nativeQuery = true)
    Optional<StudentStatus> findAndLockOldestAvailableExcluding(@Param("excludeIds") List<UUID> excludeIds);
}
