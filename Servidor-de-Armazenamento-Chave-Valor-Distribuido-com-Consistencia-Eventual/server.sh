#!/usr/bin/env bash
set -euo pipefail

# Preferir o JAR sombreado; se não existir, usar o JAR padrão
if JAR=$(ls target/*-shaded.jar 2>/dev/null | head -n1); then
  :
else
  JAR=$(ls target/*.jar 2>/dev/null | head -n1 || true)
fi

if [[ -z "${JAR:-}" ]]; then
  echo "Nenhum JAR encontrado em target/. Rode ./compile.sh primeiro." >&2
  exit 1
fi

exec java -jar "$JAR" "$@"
