package com.psicosus.queue.service;

import com.psicosus.queue.config.RabbitMQConfig;
import com.psicosus.queue.entity.QueueEntry;
import com.psicosus.queue.entity.QueueStatus;
import com.psicosus.queue.event.SessionEndedEvent;
import com.psicosus.queue.event.SessionStartedEvent;
import com.psicosus.queue.repository.QueueEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class SessionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SessionEventListener.class);

    private final QueueEntryRepository repository;

    public SessionEventListener(QueueEntryRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SESSION_STARTED)
    @Transactional
    public void onSessionStarted(SessionStartedEvent event) {
        repository.findById(event.queueEntryId()).ifPresentOrElse(entry -> {
            if (entry.getStatus() != QueueStatus.WAITING) {
                log.info("Queue entry {} already in state {}, skipping session.started transition",
                        entry.getId(), entry.getStatus());
                return;
            }
            entry.setStatus(QueueStatus.IN_PROGRESS);
            entry.setSessionId(event.sessionId());
            entry.setJitsiLink(event.jitsiLink());
            entry.setUpdatedAt(Instant.now());
            repository.save(entry);
        }, () -> log.warn("Queue entry {} not found for session.started event", event.queueEntryId()));
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SESSION_ENDED)
    @Transactional
    public void onSessionEnded(SessionEndedEvent event) {
        repository.findById(event.queueEntryId()).ifPresentOrElse(entry -> {
            if (entry.getStatus() != QueueStatus.IN_PROGRESS) {
                log.info("Queue entry {} already in state {}, skipping session.ended transition",
                        entry.getId(), entry.getStatus());
                return;
            }
            entry.setStatus(QueueStatus.ATTENDED);
            entry.setUpdatedAt(Instant.now());
            repository.save(entry);
        }, () -> log.warn("Queue entry {} not found for session.ended event", event.queueEntryId()));
    }
}
