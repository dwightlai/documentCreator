#!/usr/bin/env bash
set -euo pipefail

RESTORE_FROM="${RESTORE_FROM:?set RESTORE_FROM to one timestamped backup directory}"
CONFIRM_RESTORE="${CONFIRM_RESTORE:-}"
MINIO_BUCKET="${MINIO_BUCKET:-document-platform}"

if [[ "${CONFIRM_RESTORE}" != "RESTORE_DOCUMENT_PLATFORM" ]]; then
  echo "Refusing destructive restore. Set CONFIRM_RESTORE=RESTORE_DOCUMENT_PLATFORM." >&2
  exit 2
fi
if [[ ! -f "${RESTORE_FROM}/postgres.dump" || ! -f "${RESTORE_FROM}/SHA256SUMS" ]]; then
  echo "Backup is incomplete: ${RESTORE_FROM}" >&2
  exit 2
fi

echo "Verifying backup checksums"
(cd "${RESTORE_FROM}" && sha256sum --check SHA256SUMS)

echo "Stopping application writers"
docker compose stop backend web

restore_failed=1
finish() {
  if [[ ${restore_failed} -ne 0 ]]; then
    echo "Restore failed; backend remains stopped for investigation." >&2
  fi
}
trap finish EXIT

echo "Recreating PostgreSQL application database"
docker compose exec -T postgres sh -ceu '
  dropdb --if-exists --force --username="$POSTGRES_USER" "$POSTGRES_DB"
  createdb --username="$POSTGRES_USER" "$POSTGRES_DB"
'
docker compose exec -T postgres sh -ceu \
  'pg_restore --exit-on-error --no-owner --no-acl --dbname="$POSTGRES_DB" --username="$POSTGRES_USER"' \
  < "${RESTORE_FROM}/postgres.dump"

echo "Restoring MinIO bucket ${MINIO_BUCKET}"
docker compose run --rm --no-deps -T \
  --volume "${RESTORE_FROM}/objects:/backup:ro" \
  --entrypoint /bin/sh minio -ceu '
    mc alias set target http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
    mc mb --ignore-existing "target/'"${MINIO_BUCKET}"'"
    mc mirror --overwrite --remove /backup "target/'"${MINIO_BUCKET}"'"
  '

echo "Starting application and checking readiness"
docker compose up -d backend web
for attempt in $(seq 1 40); do
  if docker compose exec -T backend curl -fsS \
      http://127.0.0.1:18080/actuator/health/readiness >/dev/null; then
    restore_failed=0
    trap - EXIT
    echo "Restore completed and readiness is UP"
    exit 0
  fi
  sleep 5
done

echo "Readiness did not become UP within 200 seconds." >&2
exit 1
