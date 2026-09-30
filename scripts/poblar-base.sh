#!/usr/bin/env bash
# Ejecuta los 3 Jobs batch de la semana 3 para poblar la base compartida
# que consumen los BFFs (perfil demo: H2 en ~/banco-xyz-data).
cd "$(dirname "$0")/.."
for job in transacciones intereses cuentas-anuales; do
  echo "==== Ejecutando job: $job ===="
  mvn -q -pl batch-modernizacion -am spring-boot:run -Dspring-boot.run.arguments="--job=$job" || exit 1
done
echo "Base poblada."
