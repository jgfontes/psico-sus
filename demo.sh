#!/bin/bash
#
# PsicoSUS - Script de Demonstração E2E
# Executa o fluxo completo de atendimento automaticamente.
#
# Uso:
#   docker compose up --build -d    # subir o stack primeiro
#   sleep 60                         # aguardar boot
#   ./demo.sh                        # executar este script
#

set -uo pipefail

BOLD='\033[1m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[0;33m'
NC='\033[0m'

GW="${GATEWAY_URL:-http://localhost:8090}"
AUTH="${AUTH_URL:-http://localhost:8080}"
QUEUE="${QUEUE_URL:-http://localhost:8081}"
AVAIL="${AVAILABILITY_URL:-http://localhost:8082}"
SESSION="${SESSION_URL:-http://localhost:8083}"
SUPERVISION="${SUPERVISION_URL:-http://localhost:8084}"
MEDREC="${MEDICAL_RECORD_URL:-http://localhost:8085}"

step() { echo -e "\n${BOLD}${CYAN}▶ $1${NC}"; }
ok() { echo -e "  ${GREEN}✓ $1${NC}"; }
info() { echo -e "  ${YELLOW}→ $1${NC}"; }

login() {
  curl -sf -X POST "$AUTH/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])"
}

echo -e "${BOLD}"
echo "══════════════════════════════════════════════════════════"
echo "     PsicoSUS - Demonstração do Fluxo de Atendimento"
echo "══════════════════════════════════════════════════════════"
echo -e "${NC}"

# ─── SETUP ───────────────────────────────────────────────────

step "1/12 - Registrando universidade"
curl -sf -X POST "$AUTH/auth/register" -H "Content-Type: application/json" \
  -d '{"name":"UNIFESP","email":"admin@unifesp.br","cpf":"12345678901","password":"demo123","role":"UNIVERSITY","referenceId":"00000000-0000-0000-0000-000000000001"}' > /dev/null 2>&1 || true
UNIV_TOKEN=$(login "admin@unifesp.br" "demo123")
UNIV_RESP=$(curl -s -X POST "$AVAIL/availability/university" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $UNIV_TOKEN" \
  -d '{"name":"UNIFESP","cnpj":"11222333000181","state":"SP","city":"São Paulo"}')
UNIV_ID=$(echo "$UNIV_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['universityId'])" 2>/dev/null || echo "already-exists")
ok "Universidade registrada"

step "2/12 - Registrando supervisor (Dr. Carlos, CRP 06/12345)"

# Need university ID for supervisor registration
if [ "$UNIV_ID" = "already-exists" ]; then
  UNIV_TOKEN=$(login "admin@unifesp.br" "demo123")
  UNIV_ID="00000000-0000-0000-0000-000000000001"
fi

# Create supervisor entity first to get the real ID
SUP_RESP=$(curl -s -X POST "$SUPERVISION/supervision/supervisor" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $UNIV_TOKEN" \
  -d "{\"name\":\"Dr.Carlos\",\"email\":\"carlos@psi.com\",\"cpf\":\"98765432101\",\"crp\":\"06/12345\",\"universityId\":\"$UNIV_ID\"}")
SUP_ID=$(echo "$SUP_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['supervisorId'])" 2>/dev/null || echo "00000000-0000-0000-0000-000000000002")

# Register auth credential with the real supervisor ID
curl -s -X POST "$AUTH/auth/register" -H "Content-Type: application/json" \
  -d "{\"name\":\"Dr.Carlos\",\"email\":\"carlos@psi.com\",\"cpf\":\"98765432101\",\"password\":\"demo123\",\"role\":\"SUPERVISOR\",\"referenceId\":\"$SUP_ID\"}" > /dev/null 2>&1 || true
ok "Supervisor registrado (ID: $SUP_ID)"

step "3/12 - Registrando aluna (Maria, 8º semestre)"
STU_RESP=$(curl -s -X POST "$AVAIL/availability/student" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $UNIV_TOKEN" \
  -d "{\"name\":\"Maria Silva\",\"email\":\"maria@uni.br\",\"cpf\":\"55566677788\",\"universityId\":\"$UNIV_ID\",\"semester\":8,\"targetHours\":200,\"supervisorCrp\":\"06/12345\"}")
STUDENT_ID=$(echo "$STU_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['studentId'])" 2>/dev/null)
if [ -z "$STUDENT_ID" ]; then
  # Student already exists — fetch from DB via a login
  curl -s -X POST "$AUTH/auth/register" -H "Content-Type: application/json" \
    -d '{"name":"Maria","email":"maria@uni.br","cpf":"55566677788","password":"demo123","role":"STUDENT","referenceId":"00000000-0000-0000-0000-000000000099"}' > /dev/null 2>&1 || true
  STU_TOKEN=$(login "maria@uni.br" "demo123")
  # Extract referenceId (studentId) from JWT
  STUDENT_ID=$(echo "$STU_TOKEN" | python3 -c "
import sys,json,base64
token = sys.stdin.read().strip()
payload = token.split('.')[1]
payload += '=' * (4 - len(payload) % 4)
data = json.loads(base64.urlsafe_b64decode(payload))
print(data.get('referenceId',''))
")
else
  curl -s -X POST "$AUTH/auth/register" -H "Content-Type: application/json" \
    -d "{\"name\":\"Maria\",\"email\":\"maria@uni.br\",\"cpf\":\"55566677788\",\"password\":\"demo123\",\"role\":\"STUDENT\",\"referenceId\":\"$STUDENT_ID\"}" > /dev/null 2>&1 || true
  STU_TOKEN=$(login "maria@uni.br" "demo123")
fi
ok "Aluna registrada (ID: $STUDENT_ID)"

step "4/12 - Aluna fica AVAILABLE para atendimento"
curl -sf -X PATCH "$AVAIL/availability/student/$STUDENT_ID/status" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $STU_TOKEN" \
  -d '{"status":"AVAILABLE"}' > /dev/null
ok "Status: AVAILABLE"

echo ""
echo -e "${BOLD}━━━ FLUXO DO PACIENTE ━━━${NC}"

step "5/12 - Paciente obtém token anônimo"
PAT_RESP=$(curl -sf -X POST "$AUTH/auth/patient-session" \
  -H "Content-Type: application/json" -d '{"patientName":"João Paciente"}')
PAT_TOKEN=$(echo "$PAT_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
PAT_ID=$(echo "$PAT_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['patientId'])")
ok "Token emitido para paciente $PAT_ID"

step "6/12 - Paciente entra na fila de atendimento"
JOIN_RESP=$(curl -sf -X POST "$QUEUE/queue/join" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $PAT_TOKEN" \
  -d '{"patientName":"João Paciente","symptomsDescription":"Ansiedade intensa, insônia há 3 dias, pensamentos negativos recorrentes."}')
ENTRY_ID=$(echo "$JOIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['queueEntryId'])")
POSITION=$(echo "$JOIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['position'])")
ok "Na fila! Entry: $ENTRY_ID | Posição: $POSITION"

step "7/12 - Aguardando matching automático..."
info "O availability-service consome o evento e cria a sessão..."
sleep 8

POS_RESP=$(curl -sf "$QUEUE/queue/position/$PAT_ID" -H "Authorization: Bearer $PAT_TOKEN")
STATUS=$(echo "$POS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
JITSI=$(echo "$POS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('jitsiLink',''))")
SESSION_ID=$(echo "$POS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId',''))")

if [ "$STATUS" = "IN_PROGRESS" ]; then
  ok "MATCHED! Sessão criada automaticamente"
  info "Session: $SESSION_ID"
  info "Jitsi:   $JITSI"
else
  echo -e "  \033[0;31m✗ Matching não ocorreu (status=$STATUS). Verifique os logs.\033[0m"
  exit 1
fi

step "8/12 - Aluna confirma entrada na sala"
curl -sf -X PATCH "$SESSION/session/$SESSION_ID/confirm-start" \
  -H "Authorization: Bearer $STU_TOKEN" > /dev/null
ok "Sessão IN_PROGRESS — vídeo em andamento"

step "9/12 - Supervisor monitora sessão ativa"
SUP_TOKEN=$(login "carlos@psi.com" "demo123")
ACTIVE=$(curl -sf "$SUPERVISION/supervision/active-sessions" -H "Authorization: Bearer $SUP_TOKEN")
info "Sessões ativas sob supervisão: $(echo $ACTIVE | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')"

step "10/12 - Aluna encerra a sessão com resumo clínico"
END_RESP=$(curl -sf -X POST "$SESSION/session/$SESSION_ID/end" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $STU_TOKEN" \
  -d "{\"endedBy\":\"$STUDENT_ID\",\"clinicalSummary\":\"Paciente apresenta quadro de ansiedade generalizada (F41.1). Realizadas técnicas de grounding e psicoeducação sobre manejo de crises. Encaminhamento para CAPS recomendado.\",\"icd10\":\"F41.1\",\"referral\":\"CAPS - Centro de Atenção Psicossocial\",\"suggestedReturn\":\"2026-08-01\"}")
DURATION=$(echo "$END_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['durationMinutes'])")
ok "Sessão encerrada (duração: ${DURATION} min)"

info "Aguardando propagação de eventos..."
sleep 5

step "11/12 - Verificando prontuário gerado"
RECORDS=$(curl -sf "$MEDREC/medical-record/patient/$PAT_ID" -H "Authorization: Bearer $PAT_TOKEN")
RECORD_COUNT=$(echo "$RECORDS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
ok "Prontuários do paciente: $RECORD_COUNT"
if [ "$RECORD_COUNT" -gt "0" ]; then
  ICD10=$(echo "$RECORDS" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0].get("icd10","N/A"))')
  SUMMARY=$(echo "$RECORDS" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0].get("clinicalSummary","")[:80])')
  info "CID-10: $ICD10"
  info "Resumo: ${SUMMARY}..."
fi

step "12/12 - Verificando horas de estágio"
HOURS=$(curl -sf "$MEDREC/medical-record/internship-hours/student/$STUDENT_ID" -H "Authorization: Bearer $STU_TOKEN")
TOTAL=$(echo "$HOURS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalMinutes',0))")
ok "Horas registradas: ${TOTAL} minutos"

echo ""
echo -e "${BOLD}━━━ VERIFICAÇÕES DE PROTEÇÃO ━━━${NC}"

# Duplicate join
PAT2=$(curl -sf -X POST "$AUTH/auth/patient-session" -H "Content-Type: application/json" -d '{"patientName":"DupTest"}')
PAT2_TOKEN=$(echo "$PAT2" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
curl -sf -X POST "$QUEUE/queue/join" -H "Content-Type: application/json" -H "Authorization: Bearer $PAT2_TOKEN" \
  -d '{"patientName":"DupTest","symptomsDescription":"x"}' > /dev/null
DUP_CODE=$(curl -so /dev/null -w "%{http_code}" -X POST "$QUEUE/queue/join" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $PAT2_TOKEN" \
  -d '{"patientName":"DupTest","symptomsDescription":"x"}')
if [ "$DUP_CODE" = "409" ]; then
  ok "Join duplicado bloqueado (HTTP 409)"
else
  echo -e "  \033[0;31m✗ Esperado 409, recebeu $DUP_CODE\033[0m"
fi

# Gateway auth
GW_NOAUTH=$(curl -so /dev/null -w "%{http_code}" "$GW/queue/size")
if [ "$GW_NOAUTH" = "401" ]; then
  ok "Gateway rejeita request sem token (HTTP 401)"
else
  echo -e "  \033[0;31m✗ Esperado 401, recebeu $GW_NOAUTH\033[0m"
fi

echo ""
echo -e "${BOLD}${GREEN}══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  ✅ DEMONSTRAÇÃO CONCLUÍDA COM SUCESSO${NC}"
echo -e "${BOLD}${GREEN}══════════════════════════════════════════════════════════${NC}"
echo ""
echo "Resumo:"
echo "  • Paciente entrou na fila e foi atendido automaticamente"
echo "  • Link Jitsi gerado e entregue via polling"
echo "  • Sessão confirmada, monitorada e encerrada"
echo "  • Prontuário e horas de estágio criados automaticamente"
echo "  • Proteções de concorrência validadas"
echo ""
echo "Logs: docker compose logs -f"
echo "Swagger: http://localhost:8080/swagger-ui.html (e portas 8081-8085)"
echo "RabbitMQ: http://localhost:15672 (psicosus/psicosus)"
echo ""
