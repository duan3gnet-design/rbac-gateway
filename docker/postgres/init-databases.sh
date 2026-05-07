#!/bin/bash
# init-databases.sh
# Chạy một lần khi PostgreSQL container khởi động lần đầu.
# Tạo 3 databases riêng cho từng service + database sonarqube.
set -e

PGUSER="${POSTGRES_USER:-postgres}"

echo "Creating databases..."

psql -v ON_ERROR_STOP=1 --username "$PGUSER" <<-EOSQL
    CREATE DATABASE rbac_auth;
    CREATE DATABASE rbac_resource;
    CREATE DATABASE rbac_gateway;
    CREATE DATABASE sonarqube;
EOSQL

echo "Databases created: rbac_auth, rbac_resource, rbac_gateway, sonarqube"
