#!/usr/bin/env bash
# =============================================================
# install_mysql.sh
# Idempotent MySQL 8 bootstrap for the Discord Bot backend.
#
# What it does:
#   1. Installs mysql-server (if missing) via apt.
#   2. Ensures the service is running & enabled on boot.
#   3. Creates the database and the least-privilege app user.
#   4. Applies the schema (users table).
#   5. Prints a summary so you can drop the creds into .env.
#
# Prerequisites:  Ubuntu 22.04+, sudo access.
# Port:           3306 (MySQL default – left untouched).
# =============================================================
set -euo pipefail

# ── colour helpers ─────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERR]${NC}   $*"; exit 1; }

# ── tunables (edit these or override via env-vars) ─────────────
DB_NAME="${DB_NAME:-discord_bot}"
DB_USER="${DB_USER:-discord_app}"
# Generate a random password unless one is already set.
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -base64 24)}"
MYSQL_PORT=3306   # standard; change only if you really need to

# ── 1. Install MySQL Server ───────────────────────────────────
if command -v mysqld &>/dev/null; then
    info "mysql-server already installed – skipping apt install."
else
    info "Installing mysql-server …"
    sudo apt-get update  -qq
    sudo apt-get install -y -qq mysql-server
fi

# ── 2. Start & enable the service ─────────────────────────────
sudo systemctl enable  mysql --now 2>/dev/null || true
sudo systemctl start   mysql

# Quick sanity check: is port 3306 open?
if ! sudo ss -tlnp | grep -q ":${MYSQL_PORT} "; then
    # Give it a moment after start
    sleep 2
    sudo ss -tlnp | grep -q ":${MYSQL_PORT} " || error "MySQL did not bind to port ${MYSQL_PORT}."
fi
info "MySQL is running on port ${MYSQL_PORT}."

# ── 3. Create database & user ─────────────────────────────────
info "Creating database '${DB_NAME}' and user '${DB_USER}'…"

sudo mysql <<SQL
-- Database
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\`
    CHARACTER SET utf8mb4
    COLLATE     utf8mb4_unicode_ci;

-- Application user  (least-privilege: only SELECT/INSERT/UPDATE/DELETE)
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost'
    IDENTIFIED BY '${DB_PASSWORD}';

GRANT SELECT, INSERT, UPDATE, DELETE
    ON \`${DB_NAME}\`.*
    TO '${DB_USER}'@'localhost';

FLUSH PRIVILEGES;
SQL

info "User '${DB_USER}' is ready."

# ── 4. Apply schema ───────────────────────────────────────────
info "Applying schema …"

sudo mysql --database="${DB_NAME}" <<SCHEMA
-- ── users ─────────────────────────────────────────────────────
-- discord_id  : Discord snowflake (unique, never changes)
-- username    : snapshot of the tag at registration time
-- created_at  : server-side default; no app logic needed
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    discord_id  VARCHAR(24)     NOT NULL UNIQUE,
    username    VARCHAR(64)     NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX       idx_discord_id  (discord_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
SCHEMA

info "Schema applied."

# ── 5. Summary ─────────────────────────────────────────────────
echo
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  MySQL bootstrap complete – paste these into your .env     ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo
echo "  DB_HOST     = localhost"
echo "  DB_PORT     = ${MYSQL_PORT}"
echo "  DB_NAME     = ${DB_NAME}"
echo "  DB_USER     = ${DB_USER}"
echo "  DB_PASSWORD = ${DB_PASSWORD}"
echo
echo -e "${YELLOW}  ⚠  Store DB_PASSWORD securely – it will not be shown again.${NC}"
echo
