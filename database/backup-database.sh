#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/.."

mkdir -p database/backups

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="database/backups/ordersdb-${TIMESTAMP}.dump"
TMP="/tmp/ordersdb-${TIMESTAMP}.dump"

sudo -u postgres pg_dump \
    -Fc \
    ordersdb \
    -f "$TMP"

mv "$TMP" "$BACKUP"

echo
echo "Orders database backed up:"
ls -lh "$BACKUP"
