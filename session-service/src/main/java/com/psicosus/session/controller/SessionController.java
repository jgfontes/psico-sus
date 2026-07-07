package com.psicosus.session.controller;

import com.psicosus.session.dto.*;
import com.psicosus.session.security.JwtClaims;
import com.psicosus.session.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<StartSessionResponse> start(@Valid @RequestBody StartSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.start(request.patientId(), request.queueEntryId()));
    }

    @PatchMapping("/{sessionId}/confirm-start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ConfirmStartResponse> confirmStart(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.confirmStart(sessionId, JwtClaims.referenceId(jwt)));
    }

    @PostMapping("/{sessionId}/end")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EndSessionResponse> end(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID sessionId,
                                                   @Valid @RequestBody EndSessionRequest request) {
        return ResponseEntity.ok(sessionService.end(sessionId, JwtClaims.referenceId(jwt), request));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailResponse> get(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.get(sessionId));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'SERVICE')")
    public ResponseEntity<List<ActiveSessionResponse>> active() {
        return ResponseEntity.ok(sessionService.active());
    }
}
