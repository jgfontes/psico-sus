package com.psicosus.auth.controller;

import com.psicosus.auth.dto.*;
import com.psicosus.auth.entity.Role;
import com.psicosus.auth.entity.UserCredential;
import com.psicosus.auth.repository.UserCredentialRepository;
import com.psicosus.auth.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserCredentialRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (request.role() == Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "patients use POST /auth/patient-session, not /auth/register");
        }
        if (request.referenceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "referenceId is required for role " + request.role());
        }
        if (repository.existsByCpf(request.cpf())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cpf already registered");
        }

        UserCredential credential = UserCredential.builder()
                .cpf(request.cpf())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .referenceId(request.referenceId())
                .build();
        credential = repository.save(credential);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(credential.getId(), credential.getRole()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserCredential credential = repository.findByCpfAndActiveTrue(request.cpf())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        JwtService.IssuedToken issued = jwtService.issueForUser(credential.getId(), credential.getRole(), credential.getReferenceId());
        return ResponseEntity.ok(new LoginResponse(issued.accessToken(), "Bearer", issued.expiresIn(), credential.getRole()));
    }

    @PostMapping("/patient-session")
    public ResponseEntity<PatientSessionResponse> patientSession(@Valid @RequestBody PatientSessionRequest request) {
        UUID patientId = UUID.randomUUID();
        JwtService.IssuedToken issued = jwtService.issueForPatient(patientId, request.patientName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PatientSessionResponse(issued.accessToken(), "Bearer", issued.expiresIn(), patientId));
    }
}
