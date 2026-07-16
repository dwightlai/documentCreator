#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env}"
EVIDENCE_ROOT="${EVIDENCE_ROOT:-./target/release-evidence}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="${EVIDENCE_ROOT%/}/${STAMP}"

ENV_FILE="${ENV_FILE}" bash deploy/ops/preflight.sh
mkdir -p "${EVIDENCE_DIR}"

docker compose --env-file "${ENV_FILE}" config > "${EVIDENCE_DIR}/compose.resolved.yml"
docker compose --env-file "${ENV_FILE}" build --pull backend web
docker compose --env-file "${ENV_FILE}" up -d --remove-orphans

ready=0
for attempt in $(seq 1 60); do
  if docker compose --env-file "${ENV_FILE}" exec -T backend \
      curl -fsS http://127.0.0.1:18089/actuator/health/readiness >/dev/null; then
    ready=1
    break
  fi
  sleep 5
done
if [[ "${ready}" -ne 1 ]]; then
  docker compose --env-file "${ENV_FILE}" logs --no-color > "${EVIDENCE_DIR}/failed-startup.log"
  echo "Deployment failed readiness checks; services were left running for investigation." >&2
  exit 1
fi

docker compose --env-file "${ENV_FILE}" ps --format json > "${EVIDENCE_DIR}/compose-ps.json"
docker compose --env-file "${ENV_FILE}" images --format json > "${EVIDENCE_DIR}/compose-images.json"
git rev-parse --verify HEAD > "${EVIDENCE_DIR}/git-commit.txt" 2>/dev/null || echo unavailable > "${EVIDENCE_DIR}/git-commit.txt"

echo "Deployment readiness passed. Evidence: ${EVIDENCE_DIR}"
