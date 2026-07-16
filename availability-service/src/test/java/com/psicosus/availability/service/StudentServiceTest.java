package com.psicosus.availability.service;

import com.psicosus.availability.client.SupervisionServiceClient;
import com.psicosus.availability.dto.ClaimStudentResponse;
import com.psicosus.availability.entity.Student;
import com.psicosus.availability.entity.StudentStatus;
import com.psicosus.availability.entity.StudentStatusValue;
import com.psicosus.availability.repository.StudentRepository;
import com.psicosus.availability.repository.StudentStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentStatusRepository statusRepository;
    @Mock
    private SupervisionServiceClient supervisionServiceClient;
    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private StudentService studentService;

    private UUID studentId;
    private Student student;
    private StudentStatus availableStatus;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        student = Student.builder()
                .id(studentId)
                .name("Maria Silva")
                .email("maria@uni.edu")
                .cpf("12345678901")
                .universityId(UUID.randomUUID())
                .semester(8)
                .supervisorCrp("06/12345")
                .completedHours(BigDecimal.ZERO)
                .targetHours(BigDecimal.valueOf(200))
                .build();

        availableStatus = StudentStatus.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .status(StudentStatusValue.AVAILABLE)
                .updatedAt(Instant.now().minusSeconds(60))
                .build();
    }

    @Test
    @DisplayName("claimNextAvailable: locks student, marks IN_SESSION, returns details")
    void claimNextAvailable_happyPath() {
        UUID supervisorId = UUID.randomUUID();

        when(statusRepository.findAndLockOldestAvailable()).thenReturn(Optional.of(availableStatus));
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(supervisionServiceClient.resolveSupervisorIdByCrp("06/12345")).thenReturn(supervisorId);
        when(statusRepository.save(any(StudentStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimStudentResponse result = studentService.claimNextAvailable();

        assertThat(result.studentId()).isEqualTo(studentId);
        assertThat(result.name()).isEqualTo("Maria Silva");
        assertThat(result.supervisorId()).isEqualTo(supervisorId);

        // Verify IN_SESSION status was saved
        ArgumentCaptor<StudentStatus> captor = ArgumentCaptor.forClass(StudentStatus.class);
        verify(statusRepository).save(captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo(studentId);
        assertThat(captor.getValue().getStatus()).isEqualTo(StudentStatusValue.IN_SESSION);
    }

    @Test
    @DisplayName("claimNextAvailable: throws 404 when no student is available")
    void claimNextAvailable_noStudentAvailable() {
        when(statusRepository.findAndLockOldestAvailable()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.claimNextAvailable())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no student currently available");
    }

    @Test
    @DisplayName("claimNextAvailable: throws 409 when student has no supervisor CRP")
    void claimNextAvailable_noSupervisorCrp() {
        student.setSupervisorCrp(null);

        when(statusRepository.findAndLockOldestAvailable()).thenReturn(Optional.of(availableStatus));
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.claimNextAvailable())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no assigned supervisor");
    }

    @Test
    @DisplayName("claimNextAvailable: throws 409 when supervisor CRP not found")
    void claimNextAvailable_supervisorNotFound() {
        when(statusRepository.findAndLockOldestAvailable()).thenReturn(Optional.of(availableStatus));
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(supervisionServiceClient.resolveSupervisorIdByCrp("06/12345")).thenReturn(null);

        assertThatThrownBy(() -> studentService.claimNextAvailable())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("supervisor CRP not found");
    }

    @Test
    @DisplayName("updateStatus to AVAILABLE triggers matching")
    void updateStatus_toAvailable_triggersMatching() {
        when(studentRepository.existsById(studentId)).thenReturn(true);
        when(statusRepository.save(any(StudentStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.updateStatus(studentId, StudentStatusValue.AVAILABLE, "STUDENT", studentId);

        verify(matchingService).matchNextWaitingPatient();
    }

    @Test
    @DisplayName("updateStatus to OFFLINE does not trigger matching")
    void updateStatus_toOffline_noMatching() {
        when(studentRepository.existsById(studentId)).thenReturn(true);
        when(statusRepository.save(any(StudentStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.updateStatus(studentId, StudentStatusValue.OFFLINE, "STUDENT", studentId);

        verify(matchingService, never()).matchNextWaitingPatient();
    }

    @Test
    @DisplayName("markAvailableAndAddHours: adds hours and saves AVAILABLE status")
    void markAvailableAndAddHours() {
        when(statusRepository.save(any(StudentStatus.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.markAvailableAndAddHours(studentId, 45);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        // 45 min = 0.75 hours
        assertThat(captor.getValue().getCompletedHours()).isEqualByComparingTo(new BigDecimal("0.75"));
    }
}
