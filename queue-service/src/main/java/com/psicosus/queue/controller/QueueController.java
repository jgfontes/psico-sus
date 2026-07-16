package com.psicosus.queue.controller;

import com.psicosus.queue.dto.*;
import com.psicosus.queue.security.JwtClaims;
import com.psicosus.queue.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<QueueJoinResponse> join(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody QueueJoinRequest request) {
        UUID patientId = JwtClaims.subject(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.join(patientId, request));
    }

    @GetMapping("/position/{patientId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<QueuePositionResponse> position(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID patientId) {
        requireSelf(jwt, patientId);
        return ResponseEntity.ok(queueService.position(patientId));
    }

    @DeleteMapping("/leave/{patientId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID patientId) {
        requireSelf(jwt, patientId);
        queueService.leave(patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/size")
    public ResponseEntity<QueueSizeResponse> size() {
        return ResponseEntity.ok(new QueueSizeResponse(queueService.size()));
    }

    @GetMapping("/waiting")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<List<WaitingPatientDTO>> waiting() {
        var entries = queueService.waitingEntries().stream()
                .map(e -> new WaitingPatientDTO(e.getId(), e.getPatientId(), e.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(entries);
    }

    private void requireSelf(Jwt jwt, UUID patientId) {
        if (!JwtClaims.subject(jwt).equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot act on another patient's queue entry");
        }
    }
}
