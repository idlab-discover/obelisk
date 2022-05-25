#!/usr/bin/env sh

if [ "$1" = "--all" ]; then
    docker compose down -v
else
    docker compose down
fi
