package com.psicosus.availability.repository;

import com.psicosus.availability.entity.StudentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
