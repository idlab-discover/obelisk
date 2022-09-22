#!/bin/bash
cd "$(dirname "$0")" || exit
PARALLEL="$(nproc --ignore=1)"
find . -maxdepth 1 -mindepth 1 -type d -print0 | xargs -t -0 -n1 -P "$PARALLEL" helm dependency update --skip-refresh