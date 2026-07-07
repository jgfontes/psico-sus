package com.psicosus.availability.service;

import com.psicosus.availability.client.SupervisionServiceClient;
import com.psicosus.availability.dto.*;
import com.psicosus.availability.entity.Student;
import com.psicosus.availability.entity.StudentStatus;
import com.psicosus.availability.entity.StudentStatusValue;
import com.psicosus.availability.repository.StudentRepository;
import com.psicosus.availability.repository.StudentStatusRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentStatusRepository statusRepository;
    private final SupervisionServiceClient supervisionServiceClient;

    public StudentService(StudentRepository studentRepository,
                           StudentStatusRepository statusRepository,
                           SupervisionServiceClient supervisionServiceClient) {
        this.studentRepository = studentRepository;
        this.statusRepository = statusRepository;
        this.supervisionServiceClient = supervisionServiceClient;
    }

    @Transactional
    public StudentRegisterResponse register(StudentRegisterRequest request) {
        if (studentRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        if (studentRepository.existsByCpf(request.cpf())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cpf already registered");
        }

        Student student = Student.builder()
                .name(request.name())
                .email(request.email())
                .cpf(request.cpf())
                .universityId(request.universityId())
                .semester(request.semester())
                .targetHours(request.targetHours())
                .supervisorCrp(request.supervisorCrp())
                .build();
        student = studentRepository.save(student);

        statusRepository.save(StudentStatus.builder()
                .studentId(student.getId())
                .status(StudentStatusValue.OFFLINE)
                .build());

        return new StudentRegisterResponse(student.getId(), student.getName(), StudentStatusValue.OFFLINE.name());
    }

    @Transactional
    public StudentStatusUpdateResponse updateStatus(UUID studentId, StudentStatusValue newStatus,
                                                      String callerRole, UUID callerReferenceId) {
        if ("STUDENT".equals(callerRole) && !studentId.equals(callerReferenceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot change another student's status");
        }
        if (!studentRepository.existsById(studentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "student not found");
        }

        Instant now = Instant.now();
        statusRepository.save(StudentStatus.builder()
                .studentId(studentId)
                .status(newStatus)
                .updatedAt(now)
                .build());

        return new StudentStatusUpdateResponse(studentId, newStatus.name(), now);
    }

    @Transactional(readOnly = true)
    public NextStudentResponse next() {
        List<StudentStatus> latest = statusRepository.findLatestStatusPerStudent();

        StudentStatus available = latest.stream()
                .filter(s -> s.getStatus() == StudentStatusValue.AVAILABLE)
                .min(Comparator.comparing(StudentStatus::getUpdatedAt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no student currently available"));

        Student student = studentRepository.findById(available.getStudentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "student not found"));

        if (student.getSupervisorCrp() == null || student.getSupervisorCrp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "available student has no assigned supervisor");
        }

        UUID supervisorId;
        try {
            supervisorId = supervisionServiceClient.resolveSupervisorIdByCrp(student.getSupervisorCrp());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "could not resolve supervisor", e);
        }
        if (supervisorId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "supervisor CRP not found in supervision-service");
        }

        return new NextStudentResponse(student.getId(), student.getName(), supervisorId);
    }

    @Transactional(readOnly = true)
    public StudentHoursResponse hours(UUID studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "student not found"));

        BigDecimal completed = student.getCompletedHours();
        BigDecimal target = student.getTargetHours();
        double percent = target.compareTo(BigDecimal.ZERO) > 0
                ? completed.divide(target, 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;

        return new StudentHoursResponse(completed, target, percent);
    }

    @Transactional
    public void markAvailableAndAddHours(UUID studentId, int durationMinutes) {
        statusRepository.save(StudentStatus.builder()
                .studentId(studentId)
                .status(StudentStatusValue.AVAILABLE)
                .build());

        studentRepository.findById(studentId).ifPresent(student -> {
            BigDecimal additionalHours = BigDecimal.valueOf(durationMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            student.setCompletedHours(student.getCompletedHours().add(additionalHours));
            studentRepository.save(student);
        });
    }
}
