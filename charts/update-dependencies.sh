#!/bin/sh
cd "$(dirname "$0")" || exit
PARALLEL="$(nproc --ignore=1)"
find ./modules -maxdepth 2 -name Chart.yaml -exec dirname {} + | tr '\n' '\0' |  xargs -t -0 -n1 -P "$PARALLEL" helm dependency update --skip-refresh

find . -maxdepth 2 -name Chart.yaml   -exec dirname {} + | tr '\n' '\0' | xargs -t -0 -n1 -P "$PARALLEL" helm dependency update --skip-refresh
