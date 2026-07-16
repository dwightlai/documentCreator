#!/usr/bin/env bash
set -euo pipefail

# Run from the repository root with the production .env already configured.
# BACKUP_ROOT must reside on storage independent from the application volumes.
BACKUP_ROOT="${BACKUP_ROOT:?set BACKUP_ROOT to an independent backup path}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="${BACKUP_ROOT%/}/${STAMP}"
MINIO_BUCKET="${MINIO_BUCKET:-document-platform}"

umask 077
mkdir -p "${DEST}/objects"

echo "Creating PostgreSQL custom-format backup in ${DEST}"
docker compose exec -T postgres sh -ceu \
  'pg_dump --format=custom --compress=9 --no-owner --no-acl --dbname="$POSTGRES_DB" --username="$POSTGRES_USER"' \
  > "${DEST}/postgres.dump"

echo "Mirroring MinIO bucket ${MINIO_BUCKET}"
docker compose run --rm --no-deps -T \
  --volume "${DEST}/objects:/backup" \
  --entrypoint /bin/sh minio -ceu '
    mc alias set source http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
    mc mirror --overwrite "source/'"${MINIO_BUCKET}"'" /backup
  '

cat > "${DEST}/metadata.txt" <<EOF
created_at_utc=${STAMP}
database=document_platform
object_bucket=${MINIO_BUCKET}
git_commit=$(git rev-parse --verify HEAD 2>/dev/null || echo unavailable)
compose_images_begin
$(docker compose images --format json)
compose_images_end
EOF

(
  cd "${DEST}"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)

echo "Backup complete: ${DEST}"
