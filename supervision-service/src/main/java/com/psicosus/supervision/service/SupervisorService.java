package com.psicosus.supervision.service;

import com.psicosus.supervision.dto.SupervisorLookupResponse;
import com.psicosus.supervision.dto.SupervisorRegisterRequest;
import com.psicosus.supervision.dto.SupervisorRegisterResponse;
import com.psicosus.supervision.entity.Supervisor;
import com.psicosus.supervision.repository.SupervisorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupervisorService {

    private final SupervisorRepository repository;

    public SupervisorService(SupervisorRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SupervisorRegisterResponse register(SupervisorRegisterRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        if (repository.existsByCpf(request.cpf())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cpf already registered");
        }
        if (repository.existsByCrp(request.crp())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "crp already registered");
        }

        Supervisor supervisor = Supervisor.builder()
                .name(request.name())
                .email(request.email())
                .cpf(request.cpf())
                .crp(request.crp())
                .universityId(request.universityId())
                .build();
        supervisor = repository.save(supervisor);

        return new SupervisorRegisterResponse(supervisor.getId(), supervisor.getName(), supervisor.getCrp());
    }

    @Transactional(readOnly = true)
    public SupervisorLookupResponse byCrp(String crp) {
        Supervisor supervisor = repository.findByCrp(crp)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no supervisor with this CRP"));
        return new SupervisorLookupResponse(supervisor.getId(), supervisor.getName(), supervisor.getCrp());
    }
}
