# PsicoSUS

Plataforma de saúde mental que conecta **pacientes do SUS em crise** a **alunos de psicologia supervisionados**, por meio de sessões de vídeo em tempo real via Jitsi Meet.

Projeto desenvolvido como trabalho de pós-graduação.

---

## Arquitetura

```
                          ┌─────────────────────────┐
                          │      API Gateway        │
                          │   (Spring Cloud) :8090  │
                          └───────────┬─────────────┘
                                      │
          ┌───────────┬───────────┬───┼───┬───────────┬───────────┐
          │           │           │       │           │           │
   ┌──────┴──┐ ┌──────┴──┐ ┌─────┴───┐ ┌─┴───────┐ ┌┴────────┐ ┌┴──────────┐
   │  Auth   │ │  Queue  │ │ Avail.  │ │ Session │ │ Superv. │ │  Medical  │
   │ :8080   │ │ :8081   │ │ :8082   │ │ :8083   │ │ :8084   │ │  Record   │
   │         │ │         │ │         │ │         │ │         │ │  :8085    │
   └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬─────┘
        │           │           │           │           │           │
        └───────────┴───────────┴─────┬─────┴───────────┴───────────┘
                                      │
                              ┌───────┴───────┐
                              │  PostgreSQL   │
                              │   (schemas)   │
                              └───────────────┘
                                      │
          ┌───────────────────────────┴───────────────────────────┐
          │                     RabbitMQ                           │
          │  patient.joined.queue │ session.started │ session.ended │
          │  supervisor.intervened                                  │
          └────────────────────────────────────────────────────────┘
```

### Serviços

| Serviço | Porta | Responsabilidade |
|---------|-------|-----------------|
| **api-gateway** | 8090 | Ponto de entrada único, validação JWT, CORS, roteamento |
| **auth-service** | 8080 | Registro, login, emissão de tokens JWT (substitui gov.br no dev) |
| **queue-service** | 8081 | Fila de espera, posição, expiração, lifecycle |
| **availability-service** | 8082 | Universidades, alunos, status, matching automático |
| **session-service** | 8083 | Criação de sessão, link Jitsi, lifecycle da sessão |
| **supervision-service** | 8084 | Supervisores, intervenção em sessões ativas |
| **medical-record-service** | 8085 | Prontuário clínico, horas de estágio |

### Comunicação

- **Síncrona:** REST entre serviços (com tokens SERVICE internos)
- **Assíncrona:** RabbitMQ (topic exchange `psicosus.events`)

---

## Tech Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.3.4 |
| Gateway | Spring Cloud Gateway 2023.0.3 |
| Segurança | Spring Security + JWT HS256 |
| Persistência | Spring Data JPA + Hibernate |
| Banco | PostgreSQL 15 |
| Mensageria | RabbitMQ 3 |
| Vídeo | Jitsi Meet (meet.jit.si) |
| Documentação | SpringDoc OpenAPI 3 (Swagger UI) |
| Testes | JUnit 5 + Mockito + Testcontainers |
| Build | Maven |
| Container | Docker + Docker Compose |

---

## Como rodar

### Pré-requisitos
- Docker e Docker Compose

### Subir o projeto

```bash
docker compose up --build -d
```

Aguarde ~60 segundos para todos os serviços iniciarem. Verifique:

```bash
docker compose ps
```

Todos os 9 containers devem estar `Up`.

### Acessos

| Recurso | URL |
|---------|-----|
| API Gateway | http://localhost:8090 |
| Auth Swagger | http://localhost:8080/swagger-ui.html |
| Queue Swagger | http://localhost:8081/swagger-ui.html |
| Availability Swagger | http://localhost:8082/swagger-ui.html |
| Session Swagger | http://localhost:8083/swagger-ui.html |
| Supervision Swagger | http://localhost:8084/swagger-ui.html |
| Medical Record Swagger | http://localhost:8085/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (psicosus/psicosus) |

### Parar

```bash
docker compose down        # para os containers
docker compose down -v     # para e apaga os volumes (reset do banco)
```

---

## Fluxo de Atendimento

```
1. Paciente obtém token anônimo
   POST /auth/patient-session

2. Paciente entra na fila
   POST /queue/join → evento: patient.joined.queue

3. availability-service consome o evento
   → Verifica se há aluno AVAILABLE
   → Se sim, chama session-service para criar sessão

4. session-service cria a sessão
   → Claim atômico do aluno (SELECT FOR UPDATE SKIP LOCKED)
   → Gera link Jitsi: https://meet.jit.si/psicosus-{sessionId}
   → Publica session.started

5. queue-service consome session.started
   → Atualiza entry para IN_PROGRESS com sessionId + jitsiLink

6. Paciente faz polling e recebe o link
   GET /queue/position/{patientId} → status: IN_PROGRESS, jitsiLink: ...

7. Aluno confirma entrada na sala
   PATCH /session/{sessionId}/confirm-start

8. Supervisor monitora (opcional)
   GET /supervision/active-sessions
   POST /supervision/intervene/{sessionId}

9. Aluno encerra a sessão
   POST /session/{sessionId}/end → evento: session.ended

10. Eventos propagam:
    → queue-service: entry → ATTENDED
    → availability-service: aluno → AVAILABLE + dispara novo matching
    → medical-record-service: cria prontuário + registra horas
```

---

## Testes

### Unitários
```bash
cd queue-service && mvn test
cd availability-service && mvn test
cd session-service && mvn test
```

### Integração (requer Docker)
```bash
cd queue-service && mvn test -Dtest="QueueFlowIntegrationTest"
```

### E2E (com o stack rodando)
Importe a collection Postman de `postman/` e siga a ordem documentada em `postman/README.md`.

---

## Postman

A pasta `postman/` contém:
- `psicosus.postman_collection.json` — 33 requests organizados em 6 pastas
- `psicosus.postman_environment.json` — variáveis de ambiente para execução local

Os requests são encadeados automaticamente (tokens e IDs capturados entre chamadas).

---

## Estrutura do Projeto

```
psico-sus/
├── api-gateway/              # Spring Cloud Gateway
├── auth-service/             # Autenticação e tokens JWT
├── queue-service/            # Fila de atendimento
├── availability-service/     # Alunos e universidades
├── session-service/          # Sessões de vídeo (Jitsi)
├── supervision-service/      # Supervisão e intervenção
├── medical-record-service/   # Prontuário e horas de estágio
├── docs/                     # Spec e diagramas
├── postman/                  # Collection e environment
├── init.sql                  # Schema inicial do banco
├── V2__fix_concurrency_and_lifecycle.sql  # Migration de constraints
├── docker-compose.yml        # Orquestração completa
└── TODO_TASKS/               # Problemas identificados e resolvidos
```

---

## Decisões Técnicas

- **Matching event-driven + reconciliation:** O matching é disparado quando um paciente entra na fila (edge) E quando um aluno fica disponível (reconciliation). Garante que nenhum paciente fique órfão.
- **Atomic claim com FOR UPDATE SKIP LOCKED:** Previne que dois fluxos concorrentes selecionem o mesmo aluno.
- **Polling para notificação:** Sem WebSocket/SSE neste MVP — o paciente faz polling em `GET /queue/position` e recebe o link quando matched.
- **Idempotência:** `session.start` verifica se já existe sessão para o `queueEntryId` antes de criar. Eventos são processados condicionalmente (só avança estado se estiver no estado esperado).
- **Unique constraints como safety net:** Mesmo que a lógica aplicacional falhe, o banco impede estados inválidos (double-booking, duplicate join, duplicate session).

---

## Autores

Desenvolvido como projeto de pós-graduação.
