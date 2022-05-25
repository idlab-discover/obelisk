#!/bin/bash

PARALLEL="$(nproc --ignore=1)"
find charts/ -maxdepth 1 -mindepth 1 -print0 | xargs -0 -n1 -P "$PARALLEL" helm dependency update --skip-refresh
