package com.psicosus.supervision.service;

import com.psicosus.supervision.client.SessionServiceClient;
import com.psicosus.supervision.config.RabbitMQConfig;
import com.psicosus.supervision.dto.ActiveSessionResponse;
import com.psicosus.supervision.dto.InterveneResponse;
import com.psicosus.supervision.dto.LeaveResponse;
import com.psicosus.supervision.entity.Intervention;
import com.psicosus.supervision.event.SupervisorIntervenedEvent;
import com.psicosus.supervision.repository.InterventionRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InterventionService {

    private final InterventionRepository repository;
    private final SessionServiceClient sessionServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public InterventionService(InterventionRepository repository,
                                SessionServiceClient sessionServiceClient,
                                RabbitTemplate rabbitTemplate,
                                @Value("${psicosus.events.exchange}") String exchangeName) {
        this.repository = repository;
        this.sessionServiceClient = sessionServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    @Transactional(readOnly = true)
    public List<ActiveSessionResponse> activeSessions(String authorizationHeader, UUID supervisorId) {
        return sessionServiceClient.getActiveSessions(authorizationHeader).stream()
                .filter(s -> supervisorId.equals(s.supervisorId()))
                .map(s -> new ActiveSessionResponse(s.sessionId(), s.studentId(), s.studentName(),
                        s.jitsiLink(), s.startedAt(), s.currentDurationMinutes()))
                .toList();
    }

    @Transactional
    public InterveneResponse intervene(String authorizationHeader, UUID sessionId, UUID supervisorId, String reason) {
        SessionServiceClient.SessionDetail session = sessionServiceClient.getSession(authorizationHeader, sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }

        Intervention intervention = Intervention.builder()
                .sessionId(sessionId)
                .supervisorId(supervisorId)
                .reason(reason)
                .build();
        intervention = repository.save(intervention);

        rabbitTemplate.convertAndSend(exchangeName, RabbitMQConfig.ROUTING_KEY_SUPERVISOR_INTERVENED,
                new SupervisorIntervenedEvent(intervention.getId(), sessionId, supervisorId, intervention.getJoinedAt()));

        return new InterveneResponse(intervention.getId(), session.jitsiLink(), sessionId, intervention.getJoinedAt());
    }

    @Transactional
    public LeaveResponse leave(UUID interventionId) {
        Intervention intervention = repository.findById(interventionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "intervention not found"));

        Instant leftAt = Instant.now();
        intervention.setLeftAt(leftAt);
        repository.save(intervention);

        return new LeaveResponse(intervention.getId(), leftAt);
    }
}
