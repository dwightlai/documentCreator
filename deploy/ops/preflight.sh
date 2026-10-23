#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env}"
ALLOW_INSECURE_LOCAL_DEPLOY="${ALLOW_INSECURE_LOCAL_DEPLOY:-false}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Environment file not found: ${ENV_FILE}" >&2
  exit 2
fi

declare -A values=()
while IFS='=' read -r key value; do
  key="${key%$'\r'}"
  value="${value%$'\r'}"
  [[ -z "${key}" || "${key}" == \#* ]] && continue
  if [[ ! "${key}" =~ ^[A-Z][A-Z0-9_]*$ ]]; then
    echo "Invalid environment key in ${ENV_FILE}: ${key}" >&2
    exit 2
  fi
  values["${key}"]="${value}"
done < "${ENV_FILE}"

required=(
  POSTGRES_PASSWORD REDIS_PASSWORD MINIO_ROOT_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY
  KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_DB_PASSWORD OIDC_PUBLIC_URL PLATFORM_PUBLIC_URL PLATFORM_ALLOWED_ORIGINS
  ONLYOFFICE_JWT_SECRET
)
for key in "${required[@]}"; do
  value="${values[${key}]:-}"
  if [[ -z "${value}" ]]; then
    echo "Missing required value: ${key}" >&2
    exit 2
  fi
  if [[ "${value}" == *change-this* || "${value}" == *replace-with* || "${value}" == *example.gov.cn* ]]; then
    echo "Placeholder value is forbidden for deployment: ${key}" >&2
    exit 2
  fi
done

password_keys=(POSTGRES_PASSWORD REDIS_PASSWORD MINIO_ROOT_PASSWORD MINIO_SECRET_KEY KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_DB_PASSWORD ONLYOFFICE_JWT_SECRET)
for key in "${password_keys[@]}"; do
  if (( ${#values[${key}]} < 20 )); then
    echo "${key} must contain at least 20 characters" >&2
    exit 2
  fi
done

# ONLYOFFICE 与平台共用的 JWT 密钥（HS256），后端启动时同样要求至少 32 字节
if (( ${#values[ONLYOFFICE_JWT_SECRET]} < 32 )); then
  echo "ONLYOFFICE_JWT_SECRET must contain at least 32 characters" >&2
  exit 2
fi

for ((left=0; left<${#password_keys[@]}; left++)); do
  for ((right=left+1; right<${#password_keys[@]}; right++)); do
    if [[ "${values[${password_keys[left]}]}" == "${values[${password_keys[right]}]}" ]]; then
      echo "Secrets must not be reused: ${password_keys[left]} and ${password_keys[right]}" >&2
      exit 2
    fi
  done
done

if [[ "${ALLOW_INSECURE_LOCAL_DEPLOY}" != "true" ]]; then
  [[ "${values[OIDC_PUBLIC_URL]}" == https://* ]] || { echo "OIDC_PUBLIC_URL must use HTTPS" >&2; exit 2; }
  [[ "${values[PLATFORM_PUBLIC_URL]}" == https://* ]] || { echo "PLATFORM_PUBLIC_URL must use HTTPS" >&2; exit 2; }
  IFS=',' read -ra origins <<< "${values[PLATFORM_ALLOWED_ORIGINS]}"
  for origin in "${origins[@]}"; do
    [[ "${origin}" == https://* ]] || { echo "Every PLATFORM_ALLOWED_ORIGINS entry must use HTTPS: ${origin}" >&2; exit 2; }
  done
fi

public_url="${values[PLATFORM_PUBLIC_URL]%/}"
if [[ ! "${public_url}" =~ ^https?://[A-Za-z0-9._:-]+$ ]]; then
  echo "PLATFORM_PUBLIC_URL must be an origin without a path, query or fragment" >&2
  exit 2
fi
mkdir -p target/keycloak
sed "s|__WEB_ORIGIN__|${public_url}|g" deploy/keycloak/platform-realm.json > target/keycloak/platform-realm.json
grep -F "\"${public_url}/*\"" target/keycloak/platform-realm.json >/dev/null || {
  echo "Failed to render Keycloak redirect URI" >&2
  exit 2
}

command -v docker >/dev/null || { echo "docker is required" >&2; exit 2; }
docker compose version >/dev/null
docker compose --env-file "${ENV_FILE}" config --quiet
if docker compose --env-file "${ENV_FILE}" config --images | grep -E '(^|:)latest$' >/dev/null; then
  echo "Floating latest image tag is forbidden" >&2
  exit 2
fi

echo "Production preflight passed for ${ENV_FILE}"
