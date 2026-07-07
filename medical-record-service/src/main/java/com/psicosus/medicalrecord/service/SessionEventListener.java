package com.psicosus.medicalrecord.service;

import com.psicosus.medicalrecord.config.RabbitMQConfig;
import com.psicosus.medicalrecord.event.SessionEndedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SessionEventListener {

    private final MedicalRecordService medicalRecordService;

    public SessionEventListener(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SESSION_ENDED)
    public void onSessionEnded(SessionEndedEvent event) {
        medicalRecordService.onSessionEnded(event);
    }
}
