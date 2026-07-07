package com.psicosus.medicalrecord.controller;

import com.psicosus.medicalrecord.dto.PatientRecordResponse;
import com.psicosus.medicalrecord.security.JwtClaims;
import com.psicosus.medicalrecord.service.MedicalRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/medical-record")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<PatientRecordResponse>> byPatient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID patientId) {
        String role = JwtClaims.role(jwt);
        if ("PATIENT".equals(role) && !patientId.equals(JwtClaims.subject(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot view another patient's record");
        }
        return ResponseEntity.ok(medicalRecordService.byPatient(patientId));
    }
}
