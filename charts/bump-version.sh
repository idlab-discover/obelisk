#!/bin/bash
cd "$(dirname "$0")" || exit
find . -name Chart.yaml -exec sed -i -E "s/v?[0-9]+\.[0-9]+\.[0-9]+/$1/gm" {} +
