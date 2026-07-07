package com.psicosus.availability.controller;

import com.psicosus.availability.dto.UniversityRegisterRequest;
import com.psicosus.availability.dto.UniversityRegisterResponse;
import com.psicosus.availability.service.UniversityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately open (no Bearer token required, see SecurityConfig): onboarding the very first
 * partner university has no prior actor to authenticate as. A dedicated ADMIN role would be
 * the natural next step beyond this first version.
 */
@RestController
@RequestMapping("/availability/university")
public class UniversityController {

    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @PostMapping
    public ResponseEntity<UniversityRegisterResponse> register(@Valid @RequestBody UniversityRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(universityService.register(request));
    }
}
