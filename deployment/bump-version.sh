#!/bin/bash

find . -name Chart.yaml -exec sed -i -E "s/[0-9]+\.[0-9]+\.[0-9]+/$1/gm" {} +
mvn versions:set-property -Dproperty=revision -DnewVersion="$1"
