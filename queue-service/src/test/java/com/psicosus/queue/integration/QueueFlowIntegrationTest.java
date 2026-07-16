package com.psicosus.queue.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psicosus.queue.dto.QueueJoinRequest;
import com.psicosus.queue.event.SessionEndedEvent;
import com.psicosus.queue.event.SessionStartedEvent;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueueFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("psicosus")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-queue-schema.sql");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management")
            .withUser("test", "test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "test");
        registry.add("spring.rabbitmq.password", () -> "test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private com.psicosus.queue.client.AvailabilityServiceClient availabilityServiceClient;

    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static UUID queueEntryId;

    private void mockJwt(UUID patientId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("sub", patientId.toString())
                .claim("role", "PATIENT")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    private void mockServiceJwt() {
        Jwt jwt = Jwt.withTokenValue("service-token")
                .header("alg", "HS256")
                .claim("sub", "service")
                .claim("role", "SERVICE")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    @Test
    @Order(1)
    @DisplayName("POST /queue/join - patient joins queue successfully")
    void joinQueue() throws Exception {
        mockJwt(PATIENT_ID);
        when(availabilityServiceClient.activeStudentCount()).thenReturn(1L);

        QueueJoinRequest request = new QueueJoinRequest("João Teste", "Ansiedade");

        MvcResult result = mockMvc.perform(post("/queue/join")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueEntryId").isNotEmpty())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        queueEntryId = UUID.fromString(objectMapper.readTree(body).get("queueEntryId").asText());
    }

    @Test
    @Order(2)
    @DisplayName("GET /queue/position/{patientId} - check position while WAITING")
    void checkPosition() throws Exception {
        mockJwt(PATIENT_ID);

        mockMvc.perform(get("/queue/position/" + PATIENT_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @Order(3)
    @DisplayName("POST /queue/join - duplicate join returns 409")
    void duplicateJoinRejected() throws Exception {
        mockJwt(PATIENT_ID);
        when(availabilityServiceClient.activeStudentCount()).thenReturn(1L);

        QueueJoinRequest request = new QueueJoinRequest("João Teste", "Outro sintoma");

        mockMvc.perform(post("/queue/join")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    @DisplayName("session.started event transitions entry to IN_PROGRESS")
    void sessionStartedTransition() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String jitsiLink = "https://meet.jit.si/psicosus-" + sessionId;

        SessionStartedEvent event = new SessionStartedEvent(
                sessionId, PATIENT_ID, UUID.randomUUID(), UUID.randomUUID(),
                queueEntryId, jitsiLink, Instant.now());

        rabbitTemplate.convertAndSend("psicosus.events", "session.started", event);

        // Wait for async processing
        Thread.sleep(2000);

        mockJwt(PATIENT_ID);
        mockMvc.perform(get("/queue/position/" + PATIENT_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.jitsiLink").value(jitsiLink));
    }

    @Test
    @Order(5)
    @DisplayName("session.ended event transitions entry to ATTENDED")
    void sessionEndedTransition() throws Exception {
        SessionEndedEvent event = new SessionEndedEvent(
                UUID.randomUUID(), PATIENT_ID, UUID.randomUUID(), UUID.randomUUID(),
                queueEntryId, 30, LocalDate.now(),
                "Summary", "F41.1", null, null);

        rabbitTemplate.convertAndSend("psicosus.events", "session.ended", event);

        // Wait for async processing
        Thread.sleep(2000);

        // After ATTENDED, position endpoint should return 404 (no active entry)
        mockJwt(PATIENT_ID);
        mockMvc.perform(get("/queue/position/" + PATIENT_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    @DisplayName("GET /queue/size - returns current queue size")
    void queueSize() throws Exception {
        mockJwt(PATIENT_ID);

        mockMvc.perform(get("/queue/size")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)); // all entries are ATTENDED now
    }

    @Test
    @Order(7)
    @DisplayName("GET /queue/waiting - service endpoint returns waiting list")
    void waitingList() throws Exception {
        mockServiceJwt();

        mockMvc.perform(get("/queue/waiting")
                        .header("Authorization", "Bearer service-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(8)
    @DisplayName("DELETE /queue/leave - patient leaves queue")
    void leaveQueue() throws Exception {
        // Create a new entry to leave
        UUID newPatient = UUID.randomUUID();
        mockJwt(newPatient);
        when(availabilityServiceClient.activeStudentCount()).thenReturn(0L);

        QueueJoinRequest request = new QueueJoinRequest("Ana Leave", "Teste");
        mockMvc.perform(post("/queue/join")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Leave
        mockMvc.perform(delete("/queue/leave/" + newPatient)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());

        // Position should be 404 now
        mockMvc.perform(get("/queue/position/" + newPatient)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());
    }
}
