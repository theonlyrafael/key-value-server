#!/usr/bin/env bash
set -euo pipefail

# Compila e empacota
mvn -q -DskipTests=true clean package

echo
echo "===> Artefatos gerados em target/:"
ls -lh target/*.jar || true

# Mensagem amigável sobre o JAR de execução
if ls target/*-shaded.jar >/dev/null 2>&1; then
  echo "OK: JAR sombreado encontrado: $(ls target/*-shaded.jar | head -n1)"
else
  echo "Aviso: JAR sombreado (-shaded.jar) não encontrado."
  echo "       Vou usar o JAR padrão ao rodar o server.sh."
fi
