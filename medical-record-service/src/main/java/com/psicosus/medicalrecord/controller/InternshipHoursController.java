package com.psicosus.medicalrecord.controller;

import com.psicosus.medicalrecord.dto.InternshipHoursSummaryResponse;
import com.psicosus.medicalrecord.dto.ValidateHoursResponse;
import com.psicosus.medicalrecord.security.JwtClaims;
import com.psicosus.medicalrecord.service.MedicalRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/medical-record/internship-hours")
public class InternshipHoursController {

    private final MedicalRecordService medicalRecordService;

    public InternshipHoursController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'SUPERVISOR')")
    public ResponseEntity<InternshipHoursSummaryResponse> byStudent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID studentId) {
        String role = JwtClaims.role(jwt);
        if ("STUDENT".equals(role) && !studentId.equals(JwtClaims.referenceId(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot view another student's hours");
        }
        return ResponseEntity.ok(medicalRecordService.internshipHours(studentId));
    }

    @PatchMapping("/{internshipHoursId}/validate")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<ValidateHoursResponse> validate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID internshipHoursId) {
        UUID supervisorId = JwtClaims.referenceId(jwt);
        return ResponseEntity.ok(medicalRecordService.validate(internshipHoursId, supervisorId));
    }
}
