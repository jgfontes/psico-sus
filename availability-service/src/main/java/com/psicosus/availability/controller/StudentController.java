package com.psicosus.availability.controller;

import com.psicosus.availability.dto.*;
import com.psicosus.availability.security.JwtClaims;
import com.psicosus.availability.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/availability/student")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('UNIVERSITY')")
    public ResponseEntity<StudentRegisterResponse> register(@Valid @RequestBody StudentRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.register(request));
    }

    @PatchMapping("/{studentId}/status")
    @PreAuthorize("hasAnyRole('STUDENT', 'SERVICE')")
    public ResponseEntity<StudentStatusUpdateResponse> updateStatus(@AuthenticationPrincipal Jwt jwt,
                                                                      @PathVariable UUID studentId,
                                                                      @Valid @RequestBody StudentStatusUpdateRequest request) {
        String role = JwtClaims.role(jwt);
        UUID referenceId = "STUDENT".equals(role) ? JwtClaims.referenceId(jwt) : null;
        return ResponseEntity.ok(studentService.updateStatus(studentId, request.status(), role, referenceId));
    }

    @GetMapping("/next")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<NextStudentResponse> next() {
        return ResponseEntity.ok(studentService.next());
    }

    @GetMapping("/{studentId}/hours")
    @PreAuthorize("hasAnyRole('STUDENT', 'SUPERVISOR')")
    public ResponseEntity<StudentHoursResponse> hours(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID studentId) {
        String role = JwtClaims.role(jwt);
        if ("STUDENT".equals(role) && !studentId.equals(JwtClaims.referenceId(jwt))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "cannot view another student's hours");
        }
        return ResponseEntity.ok(studentService.hours(studentId));
    }
}
