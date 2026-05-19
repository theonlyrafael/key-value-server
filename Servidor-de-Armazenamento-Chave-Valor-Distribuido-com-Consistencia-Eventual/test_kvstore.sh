#!/usr/bin/env bash
set -euo pipefail

# =========================
# Configuração (edite se quiser)
# =========================
A_ADDR="${A_ADDR:-127.0.0.1:50051}"   # Nó A
B_ADDR="${B_ADDR:-127.0.0.1:50052}"   # Nó B
KEY_BASICO="produto_1_auto"
VAL_BASICO="notebook_azul_auto"
KEY_SUBST="produto_2_auto"
VAL1="v1_auto"
VAL2="v2_auto"

# =========================
# UX helpers
# =========================
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
pass() { echo -e "${GREEN}[OK]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }
info() { echo -e "${BLUE}[*]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }

# =========================
# Descobrir como rodar o cliente (client.sh, cargo run, binário)
# =========================
run_client() {
  local addr="$1"; shift
  if [[ -x "./client.sh" ]]; then
    ./client.sh "$addr" "$@"
  elif command -v cargo >/dev/null 2>&1; then
    cargo run -- "$addr" "$@"
  elif [[ -x "./target/debug/kv_client" ]]; then
    ./target/debug/kv_client "$addr" "$@"
  else
    fail "Não encontrei client.sh, cargo ou target/debug/kv_client. Rode este script dentro do repositório do cliente oficial."
  fi
}

# Verifica se um endereço está aceitando conexões (porta aberta)
wait_port() {
  local addr="$1" ; local host="${addr%%:*}" ; local port="${addr##*:}"
  for _ in {1..30}; do
    if (echo > /dev/tcp/$host/$port) >/dev/null 2>&1; then return 0; fi
    sleep 0.2
  done
  return 1
}

# Validadores
expect_one_version() { echo "$1" | grep -q "Received 1 version(s)"; }
expect_two_versions() { echo "$1" | grep -q "Received 2 version(s)"; }
expect_value_in_versions() { echo "$1" | grep -q "Value: $2"; }

# =========================
# Helpers p/ estratégia determinística (opcional)
# =========================
stop_broker() {
  if [[ "${OFFLINE_CONFLICT:-0}" == "1" ]]; then
    info "Parando broker MQTT (sudo systemctl stop mosquitto)"
    sudo systemctl stop mosquitto || warn "Falha ao parar mosquitto (tente manualmente com 'sudo pkill mosquitto')"
    sleep 0.6
  fi
}
start_broker() {
  if [[ "${OFFLINE_CONFLICT:-0}" == "1" ]]; then
    info "Iniciando broker MQTT (sudo systemctl start mosquitto)"
    sudo systemctl start mosquitto || warn "Falha ao iniciar mosquitto (tente 'mosquitto -v &' manualmente)"
    # aguarda o broker ouvir na 1883
    for _ in {1..15}; do
      if (echo > /dev/tcp/127.0.0.1/1883) >/dev/null 2>&1; then break; fi
      sleep 0.3
    done
    sleep 0.5
  fi
}

# =========================
# Check prévio
# =========================
info "Verificando se os nós estão de pé: A=${A_ADDR}, B=${B_ADDR}"
wait_port "$A_ADDR" || fail "Nó A (${A_ADDR}) não está aceitando conexões gRPC."
wait_port "$B_ADDR" || fail "Nó B (${B_ADDR}) não está aceitando conexões gRPC."
info "OK, ambos os nós respondem."

# =========================
# 1) Replicação básica
# =========================
info "Teste 1/3: Replicação básica (PUT em A, GET em B)"
run_client "$A_ADDR" put "$KEY_BASICO" "$VAL_BASICO" >/tmp/put_basic.out 2>&1 || true
sleep 0.8  # tempo p/ replicar via MQTT
GET_OUT="$(run_client "$B_ADDR" get "$KEY_BASICO" 2>&1 || true)"
echo "$GET_OUT" > /tmp/get_basic.out
expect_one_version "$GET_OUT" || fail "GET básico não retornou 1 versão (veja /tmp/get_basic.out)"
expect_value_in_versions "$GET_OUT" "$VAL_BASICO" || fail "GET básico não contém o valor esperado '$VAL_BASICO' (veja /tmp/get_basic.out)"
pass "Replicação básica OK"

# =========================
# 2) Conflito (duas versões ativas)
# =========================
if [[ "${OFFLINE_CONFLICT:-0}" == "1" ]]; then
  info "Teste 2/3: Conflito determinístico (OFFLINE_CONFLICT=1)"

  KEY_CONFLITO="itemX_offline_$RANDOM"
  VAL_A="valor_A_$RANDOM"
  VAL_B="valor_B_$RANDOM"

  stop_broker
  ( run_client "$A_ADDR" put "$KEY_CONFLITO" "$VAL_A" >/tmp/put_conflict_A.out 2>&1 || true )
  ( run_client "$B_ADDR" put "$KEY_CONFLITO" "$VAL_B" >/tmp/put_conflict_B.out 2>&1 || true )
  start_broker

  sleep 1.2
  GET_OUT_CONFLICT="$(run_client "$A_ADDR" get "$KEY_CONFLITO" 2>&1 || true)"
  echo "$GET_OUT_CONFLICT" > /tmp/get_conflict.out

  if expect_two_versions "$GET_OUT_CONFLICT"; then
    pass "Conflito OK (duas versões ativas após religar broker)"
  else
    fail "GET conflito não retornou 2 versões (veja /tmp/get_conflict.out)"
  fi
else
  info "Teste 2/3: Conflito (PUT simultâneo com retries)"

  MAX_TRIES=12
  SUCCESS_CONFLICT=0
  for attempt in $(seq 1 $MAX_TRIES); do
    KEY_CONFLITO="itemX_auto_$RANDOM"
    VAL_A="valor_A_auto_$RANDOM"
    VAL_B="valor_B_auto_$RANDOM"

    info "Tentativa ${attempt}/${MAX_TRIES} com chave=${KEY_CONFLITO}"
    ( run_client "$A_ADDR" put "$KEY_CONFLITO" "$VAL_A" >/tmp/put_conflict_A.out 2>&1 || true ) & pidA=$!
    ( run_client "$B_ADDR" put "$KEY_CONFLITO" "$VAL_B" >/tmp/put_conflict_B.out 2>&1 || true ) & pidB=$!

    wait $pidA || warn "PUT conflito A retornou código != 0 (veja /tmp/put_conflict_A.out)"
    wait $pidB || warn "PUT conflito B retornou código != 0 (veja /tmp/put_conflict_B.out)"

    sleep 0.9
    GET_OUT_CONFLICT="$(run_client "$A_ADDR" get "$KEY_CONFLITO" 2>&1 || true)"
    echo "$GET_OUT_CONFLICT" > /tmp/get_conflict.out

    if expect_two_versions "$GET_OUT_CONFLICT"; then
      pass "Conflito OK (duas versões ativas) na tentativa ${attempt}"
      SUCCESS_CONFLICT=1
      break
    else
      warn "Ainda não simultâneo (veja /tmp/get_conflict.out). Tentando novamente..."
      sleep 0.$((RANDOM % 7 + 3))
    fi
  done

  if [[ "$SUCCESS_CONFLICT" -ne 1 ]]; then
    fail "GET conflito não retornou 2 versões após ${MAX_TRIES} tentativas (veja /tmp/get_conflict.out)"
  fi
fi

# =========================
# 3) Substituição (uma versão sucede a outra)
# =========================
info "Teste 3/3: Substituição (PUT v1 e depois v2 no mesmo nó)"
run_client "$A_ADDR" put "$KEY_SUBST" "$VAL1" >/tmp/put_subst_v1.out 2>&1 || true
sleep 0.2
run_client "$A_ADDR" put "$KEY_SUBST" "$VAL2" >/tmp/put_subst_v2.out 2>&1 || true
sleep 0.8
GET_OUT_SUBST="$(run_client "$B_ADDR" get "$KEY_SUBST" 2>&1 || true)"
echo "$GET_OUT_SUBST" > /tmp/get_subst.out
expect_one_version "$GET_OUT_SUBST" || fail "GET substituição não retornou 1 versão (veja /tmp/get_subst.out)"
expect_value_in_versions "$GET_OUT_SUBST" "$VAL2" || fail "GET substituição não retornou o valor mais novo '$VAL2' (veja /tmp/get_subst.out)"
pass "Substituição OK (versão mais nova domina)"

echo
pass "Todos os testes passaram! 🎉"
echo -e "${YELLOW}Logs salvos em:${NC} /tmp/put_basic.out /tmp/get_basic.out /tmp/put_conflict_A.out /tmp/put_conflict_B.out /tmp/get_conflict.out /tmp/put_subst_v1.out /tmp/put_subst_v2.out /tmp/get_subst.out"
