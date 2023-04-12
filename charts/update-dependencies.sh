#!/bin/bash
cd "$(dirname "$0")" || exit
PARALLEL="$(nproc --ignore=1)"
find ./modules -maxdepth 2 -name Chart.yaml  -printf "%h\0" | xargs -t -0 -n1 -P "$PARALLEL" helm dependency update --skip-refresh

find . -maxdepth 2 -name Chart.yaml  -printf "%h\0" | xargs -t -0 -n1 -P "$PARALLEL" helm dependency update --skip-refresh
