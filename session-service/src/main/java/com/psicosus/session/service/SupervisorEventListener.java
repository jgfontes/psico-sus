package com.psicosus.session.service;

import com.psicosus.session.config.RabbitMQConfig;
import com.psicosus.session.event.SupervisorIntervenedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SupervisorEventListener {

    private final SessionService sessionService;

    public SupervisorEventListener(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SUPERVISOR_INTERVENED)
    public void onSupervisorIntervened(SupervisorIntervenedEvent event) {
        sessionService.recordSupervisorJoined(event.sessionId(), event.supervisorId());
    }
}
