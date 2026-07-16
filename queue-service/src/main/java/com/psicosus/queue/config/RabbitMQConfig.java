package com.psicosus.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_SESSION_STARTED = "queue.session-started";
    public static final String QUEUE_SESSION_ENDED = "queue.session-ended";

    @Value("${psicosus.events.exchange}")
    private String exchangeName;

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue sessionStartedQueue() {
        return new Queue(QUEUE_SESSION_STARTED, true);
    }

    @Bean
    public Queue sessionEndedQueue() {
        return new Queue(QUEUE_SESSION_ENDED, true);
    }

    @Bean
    public Binding sessionStartedBinding(Queue sessionStartedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(sessionStartedQueue).to(eventsExchange).with("session.started");
    }

    @Bean
    public Binding sessionEndedBinding(Queue sessionEndedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(sessionEndedQueue).to(eventsExchange).with("session.ended");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
                                          MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
