#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${FLASK_PORT:-8000}"
HEALTH_URL="http://127.0.0.1:${PORT}/health"
LOG_FILE="${ROOT_DIR}/server.log"
PID_FILE="${ROOT_DIR}/server.pid"
CHECK_INTERVAL_SECONDS="${CHECK_INTERVAL_SECONDS:-10}"

cd "${ROOT_DIR}" || exit 1

is_healthy() {
  curl -fsS --max-time 3 "${HEALTH_URL}" >/dev/null 2>&1
}

server_running() {
  pgrep -f "${ROOT_DIR}/venv/bin/python run.py" >/dev/null 2>&1 || \
    pgrep -f "./venv/bin/python run.py" >/dev/null 2>&1
}

start_server() {
  if server_running || is_healthy; then
    return 0
  fi

  printf '%s starting backend on port %s\n' "$(date -Is)" "${PORT}" >> "${LOG_FILE}"
  nohup "${ROOT_DIR}/venv/bin/python" run.py >> "${LOG_FILE}" 2>&1 &
  printf '%s\n' "$!" > "${PID_FILE}"
}

while true; do
  if ! is_healthy; then
    start_server
  fi
  sleep "${CHECK_INTERVAL_SECONDS}"
done
