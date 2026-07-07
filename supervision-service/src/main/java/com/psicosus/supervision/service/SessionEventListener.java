package com.psicosus.supervision.service;

import com.psicosus.supervision.config.RabbitMQConfig;
import com.psicosus.supervision.event.SessionStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * supervision-service doesn't keep its own read-model of active sessions — it queries
 * session-service live (see InterventionService.activeSessions). This listener exists so the
 * documented session.started -> supervision-service integration isn't left unimplemented.
 */
@Component
public class SessionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SessionEventListener.class);

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SESSION_STARTED)
    public void onSessionStarted(SessionStartedEvent event) {
        log.info("session {} started for supervisor {}", event.sessionId(), event.supervisorId());
    }
}
