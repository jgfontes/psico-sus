package com.psicosus.supervision.controller;

import com.psicosus.supervision.dto.*;
import com.psicosus.supervision.security.JwtClaims;
import com.psicosus.supervision.service.InterventionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/supervision")
public class InterventionController {

    private final InterventionService interventionService;

    public InterventionController(InterventionService interventionService) {
        this.interventionService = interventionService;
    }

    @GetMapping("/active-sessions")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<List<ActiveSessionResponse>> activeSessions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Authorization") String authorization) {
        UUID supervisorId = JwtClaims.referenceId(jwt);
        return ResponseEntity.ok(interventionService.activeSessions(authorization, supervisorId));
    }

    @PostMapping("/intervene/{sessionId}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<InterveneResponse> intervene(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID sessionId,
            @Valid @RequestBody InterveneRequest request) {
        UUID supervisorId = JwtClaims.referenceId(jwt);
        return ResponseEntity.ok(interventionService.intervene(authorization, sessionId, supervisorId, request.reason()));
    }

    @PatchMapping("/intervention/{interventionId}/leave")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<LeaveResponse> leave(@PathVariable UUID interventionId) {
        return ResponseEntity.ok(interventionService.leave(interventionId));
    }
}
