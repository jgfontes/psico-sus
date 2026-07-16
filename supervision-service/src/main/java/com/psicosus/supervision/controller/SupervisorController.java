package com.psicosus.supervision.controller;

import com.psicosus.supervision.dto.SupervisorLookupResponse;
import com.psicosus.supervision.dto.SupervisorRegisterRequest;
import com.psicosus.supervision.dto.SupervisorRegisterResponse;
import com.psicosus.supervision.service.SupervisorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/supervision/supervisor")
public class SupervisorController {

    private final SupervisorService supervisorService;

    public SupervisorController(SupervisorService supervisorService) {
        this.supervisorService = supervisorService;
    }

    @PostMapping
    @PreAuthorize("hasRole('UNIVERSITY')")
    public ResponseEntity<SupervisorRegisterResponse> register(@Valid @RequestBody SupervisorRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supervisorService.register(request));
    }

    /**
     * Added beyond the original spec: availability-service only stores a student's supervisor
     * CRP (not a UUID), and resolves it to a supervisorId through this lookup when assembling
     * GET /availability/student/next's response.
     *
     * CRP values contain a slash (e.g. "06/12345") which cannot appear in a path segment
     * without special Tomcat configuration. Using a request parameter instead.
     */
    @GetMapping("/by-crp")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<SupervisorLookupResponse> byCrp(@RequestParam String crp) {
        return ResponseEntity.ok(supervisorService.byCrp(crp));
    }
}
