package com.psicosus.availability.service;

import com.psicosus.availability.dto.UniversityRegisterRequest;
import com.psicosus.availability.dto.UniversityRegisterResponse;
import com.psicosus.availability.entity.University;
import com.psicosus.availability.repository.UniversityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UniversityService {

    private final UniversityRepository repository;

    public UniversityService(UniversityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UniversityRegisterResponse register(UniversityRegisterRequest request) {
        if (repository.existsByCnpj(request.cnpj())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cnpj already registered");
        }

        University university = University.builder()
                .name(request.name())
                .cnpj(request.cnpj())
                .state(request.state().toUpperCase())
                .city(request.city())
                .build();
        university = repository.save(university);

        return new UniversityRegisterResponse(university.getId(), university.getName());
    }
}
