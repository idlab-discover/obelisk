#!/usr/bin/env bash

# List all base services (always on)
services=("pulsar" "mongodb" "clickhouse" "redis" "gubernator" "prometheus");

# Default states
addCatalog=true
addConsole=true
addDocs=false

# Help function
usage()
{
  echo "Usage:  ./start.sh [ -c | --catalog ] [ -a | --apiconsole ] [ -d | --docs ]"
  echo ""
  echo "        -c, --catalog     Start the Oblx stack without web-catalog"
  echo "        -a, --apiconsole  Start the Oblx stack without web-apiconsole"
  echo "        -d, --docs        Start the Oblx stack with the live mkdocs server (http://localhost:8888)"
  echo ""
  exit 2
}

# Capture arguments
PARSED_ARGUMENTS=$(getopt -n "start.sh" -o cadh --long catalog,apiconsole,docs,help -- "$@")
VALID_ARGUMENTS=$?
if [ "$VALID_ARGUMENTS" != "0" ]; then
  usage
fi

# End argumentlist
eval set -- "$PARSED_ARGUMENTS"

# Parse arguments
while :
do
  case "$1" in
    -c | --catalog)
      addCatalog=false;
      echo "$1: not starting web-catalog..";
      shift
      ;;
    -a | --apiconsole)
      addConsole=false;
      echo "$1: not starting web-apiconsole..";
      shift
      ;;
    -d | --docs)
      addDocs=true;
      echo "$1: starting mkdocs live server at http://localhost:8888 ..";
      shift
      ;;
    -h | --help)
      usage
      shift
      ;;
    --) shift; break;;
    *)
      echo "Unexpected option: $1 - this should not happen.";
      usage ;;
  esac
done

# Add correct services
if [ "$addCatalog" = true ]; then
  services+=("web-catalog")
fi
if [ "$addConsole" = true ]; then
  services+=("web-apiconsole")
fi
if [ "$addDocs" = true ]; then
  services+=("mkdocs")
fi

# Pull the latest containers of selected services
docker compose pull "${services[@]}"

# Up all selected services
docker compose up -d "${services[@]}"
