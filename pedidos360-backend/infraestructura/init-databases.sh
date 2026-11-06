#!/bin/bash
set -e
set -u

# Script de inicializacion para la imagen oficial postgres:16-alpine
# Permite crear multiples bases de datos logicas independientes en un solo servidor PostgreSQL.

function create_database() {
    local database=$1
    echo "  [INIT-DB] Verificando / creando base de datos: '$database'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        SELECT 'CREATE DATABASE $database' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$database')\gexec
        GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;
EOSQL
}

if [ -n "${APP_DATABASES:-}" ]; then
    echo "[INIT-DB] Creando bases de datos multiples configuradas en APP_DATABASES: $APP_DATABASES"
    for db in $(echo $APP_DATABASES | tr ',' ' '); do
        create_database "$db"
    done
    echo "[INIT-DB] Bases de datos logicas inicializadas con exito."
fi

