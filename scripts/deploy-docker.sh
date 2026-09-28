#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_DIR}/.env"

usage() {
  cat <<'EOF'
Usage: ./scripts/deploy-docker.sh

Build and start Analytics Advisor with Docker Compose. The deployment includes
PostgreSQL for users, roles, scopes, saved queries, audit data, and application
configuration. PostgreSQL data is kept in the named Docker volume
"postgresdata" and survives container recreation.

Optional environment variables:
  SRSE_BOOTSTRAP_SUPER_ADMIN_USERNAME  First-start admin username (default: superadmin)
  SRSE_BOOTSTRAP_SUPER_ADMIN_PASSWORD  First-start admin password (generated when absent)
  SRSE_OPERATIONAL_PASSWORD            PostgreSQL application password (generated when absent)
  SRSE_EXTERNAL_SOURCE_SECRET_KEY      Encrypts external DB passwords (generated when absent)
  NEXT_PUBLIC_API_BASE                 Browser-visible backend URL (default: http://localhost:8080)
  SRSE_FRONTEND_ORIGINS                Allowed frontend origin (default: http://localhost:3000)

The bootstrap password is used only when app_user is empty. Re-running this
script never resets an existing user or password. This script never removes
Docker volumes.
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 0 ]]; then
  usage >&2
  exit 2
fi

for command_name in docker curl awk mktemp openssl; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command not found: ${command_name}" >&2
    exit 1
  fi
done

if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but is not running. Start Docker and run this script again." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (the 'docker compose' command)." >&2
  exit 1
fi

generate_secret() {
  openssl rand -hex 24
}

generate_encryption_key() {
  openssl rand -base64 32 | tr -d '\n'
}

read_env_value() {
  local key="$1"
  [[ -f "${ENV_FILE}" ]] || return 0
  awk -F= -v wanted="${key}" '$1 == wanted { sub(/^[^=]*=/, ""); value=$0 } END { print value }' "${ENV_FILE}"
}

upsert_env_value() {
  local key="$1"
  local value="$2"
  local temp_file
  temp_file="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"

  if [[ -f "${ENV_FILE}" ]]; then
    awk -v wanted="${key}" -v replacement="${key}=${value}" '
      BEGIN { replaced=0 }
      index($0, wanted "=") == 1 {
        if (!replaced) print replacement
        replaced=1
        next
      }
      { print }
      END { if (!replaced) print replacement }
    ' "${ENV_FILE}" > "${temp_file}"
  else
    printf '%s=%s\n' "${key}" "${value}" > "${temp_file}"
  fi

  mv "${temp_file}" "${ENV_FILE}"
}

cd "${PROJECT_DIR}"
touch "${ENV_FILE}"
chmod 600 "${ENV_FILE}"

admin_username="${SRSE_BOOTSTRAP_SUPER_ADMIN_USERNAME:-$(read_env_value SRSE_BOOTSTRAP_SUPER_ADMIN_USERNAME)}"
admin_username="${admin_username:-superadmin}"

admin_password="${SRSE_BOOTSTRAP_SUPER_ADMIN_PASSWORD:-$(read_env_value SRSE_BOOTSTRAP_SUPER_ADMIN_PASSWORD)}"
admin_password_generated=false
if [[ -z "${admin_password}" ]]; then
  admin_password="$(generate_secret)"
  admin_password_generated=true
fi

operational_password="${SRSE_OPERATIONAL_PASSWORD:-$(read_env_value SRSE_OPERATIONAL_PASSWORD)}"
if [[ -z "${operational_password}" || "${operational_password}" == "change_me" ]]; then
  existing_postgres_container="$(docker compose ps --all --quiet postgres 2>/dev/null || true)"
  if [[ -n "${existing_postgres_container}" ]]; then
    operational_password="$(
      docker inspect "${existing_postgres_container}" --format '{{range .Config.Env}}{{println .}}{{end}}' \
        | awk -F= '$1 == "POSTGRES_PASSWORD" { sub(/^[^=]*=/, ""); print; exit }'
    )"
  fi
  operational_password="${operational_password:-$(generate_secret)}"
fi

external_source_key="${SRSE_EXTERNAL_SOURCE_SECRET_KEY:-$(read_env_value SRSE_EXTERNAL_SOURCE_SECRET_KEY)}"
if [[ -z "${external_source_key}" ]]; then
  external_source_key="$(generate_encryption_key)"
fi

api_base="${NEXT_PUBLIC_API_BASE:-$(read_env_value NEXT_PUBLIC_API_BASE)}"
api_base="${api_base:-http://localhost:8080}"
frontend_origin="${SRSE_FRONTEND_ORIGINS:-$(read_env_value SRSE_FRONTEND_ORIGINS)}"
frontend_origin="${frontend_origin:-http://localhost:3000}"

upsert_env_value DATA_MODE synthetic
upsert_env_value SRSE_AUTH_MODE local
upsert_env_value NEXT_PUBLIC_AUTH_MODE local
upsert_env_value NEXT_PUBLIC_API_BASE "${api_base}"
upsert_env_value SRSE_FRONTEND_ORIGINS "${frontend_origin}"
upsert_env_value SRSE_BOOTSTRAP_SUPER_ADMIN_USERNAME "${admin_username}"
upsert_env_value SRSE_BOOTSTRAP_SUPER_ADMIN_PASSWORD "${admin_password}"
upsert_env_value SRSE_OPERATIONAL_PASSWORD "${operational_password}"
upsert_env_value SRSE_EXTERNAL_SOURCE_SECRET_KEY "${external_source_key}"
chmod 600 "${ENV_FILE}"

echo "Building Analytics Advisor and installing its Docker services..."
docker compose --env-file "${ENV_FILE}" up -d --build

echo "Waiting for PostgreSQL and the application health checks..."
health_url="${api_base%/}/api/health/planes"
login_url="${frontend_origin%/}/login"
deadline=$((SECONDS + 300))

while (( SECONDS < deadline )); do
  health_body="$(curl -fsS "${health_url}" 2>/dev/null || true)"
  if [[ "${health_body}" == *'"operational":"up"'* && "${health_body}" == *'"analytical":"up"'* ]] \
    && curl -fsS -o /dev/null "${login_url}" 2>/dev/null; then
    echo "Analytics Advisor is ready."
    echo "Frontend: ${frontend_origin}"
    echo "Login:    ${login_url}"
    echo "Username: ${admin_username}"
    if [[ "${admin_password_generated}" == true ]]; then
      echo "Initial password: ${admin_password}"
      echo "Store this password securely and change it immediately after first login."
    else
      echo "The existing/configured initial password was preserved."
    fi
    echo "Environment file: ${ENV_FILE} (permissions 600)"
    echo "PostgreSQL data volume: $(basename "${PROJECT_DIR}")_postgresdata"
    echo "Do not use 'docker compose down -v' unless you intentionally want to erase application data."
    docker compose --env-file "${ENV_FILE}" ps
    exit 0
  fi
  sleep 3
done

echo "Deployment did not become healthy within 5 minutes." >&2
docker compose --env-file "${ENV_FILE}" ps >&2
echo "Backend logs:" >&2
docker compose --env-file "${ENV_FILE}" logs --tail=120 srse-backend >&2
exit 1
