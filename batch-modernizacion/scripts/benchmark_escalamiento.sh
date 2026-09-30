#!/usr/bin/env bash
#
# Ejecuta el Job de transacciones (multi-thread) y el Job de cuentas anuales
# (particionado) variando sus parámetros de escalamiento, y registra el
# tiempo de ejecución de cada combinación. Esto es lo que la pauta pide
# como "comparar diferentes parámetros para encontrar la configuración
# óptima" (criterio 4).
#
# Requisitos previos:
#   1) mvn clean package   (genera target/batch-modernizacion-1.0.0.jar)
#   2) ./scripts/generar_dataset_benchmark.sh   (genera datasets grandes)
#
# Uso:
#   ./scripts/benchmark_escalamiento.sh
#
# Salida:
#   benchmark-output/resultados_escalamiento.csv
#   Un resumen impreso en consola al final.
#
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

JAR="target/batch-modernizacion-1.0.0.jar"
DATA_DIR="$RAIZ/benchmark-data"
OUT_DIR="$RAIZ/benchmark-output"
CSV_RESULTADOS="$OUT_DIR/resultados_escalamiento.csv"

if [ ! -f "$JAR" ]; then
  echo "ERROR: no existe $JAR. Ejecuta primero: mvn clean package"
  exit 1
fi
if [ ! -f "$DATA_DIR/transacciones_grande.csv" ] || [ ! -f "$DATA_DIR/cuentas_anuales_grande.csv" ]; then
  echo "ERROR: faltan los datasets grandes. Ejecuta primero: ./scripts/generar_dataset_benchmark.sh"
  exit 1
fi

mkdir -p "$OUT_DIR"
echo "tipo_prueba,parametro,valor,duracion_ms,estado" > "$CSV_RESULTADOS"

ejecutar() {
  local tag="$1"; shift
  local inicio fin duracion estado
  inicio=$(date +%s%N)
  if java -jar "$JAR" "$@" > "$OUT_DIR/log_${tag}.txt" 2>&1; then
    estado="OK"
  else
    estado="FALLO"
  fi
  fin=$(date +%s%N)
  duracion=$(( (fin - inicio) / 1000000 ))
  echo "$duracion" # se retorna por stdout para capturarlo
  echo "  -> [$tag] duracion=${duracion}ms estado=${estado}" >&2
  echo "$tag,,,${duracion},${estado}" >> /dev/null # placeholder, se sobreescribe abajo
  echo "$estado" > "$OUT_DIR/estado_${tag}.txt"
}

echo "==================================================================="
echo " BENCHMARK 1/2: escalamiento MULTI-THREAD (Job transacciones)"
echo "==================================================================="
for combo in "1:1" "2:2" "4:8" "8:16"; do
  core="${combo%%:*}"
  max="${combo##*:}"
  tag="hilos_${core}_${max}"
  dur=$(ejecutar "$tag" \
      --job=transacciones \
      --spring.profiles.active=demo \
      --spring.datasource.url="jdbc:h2:mem:bench_${tag};DB_CLOSE_DELAY=-1" \
      --app.archivos.transacciones="file:${DATA_DIR}/transacciones_grande.csv" \
      --app.batch.chunk-size=200 \
      --app.batch.hilos.core="${core}" \
      --app.batch.hilos.max="${max}" \
      --app.reportes.directorio="${OUT_DIR}/${tag}")
  estado=$(cat "$OUT_DIR/estado_${tag}.txt")
  echo "transacciones,hilos_core_max,${core}/${max},${dur},${estado}" >> "$CSV_RESULTADOS"
done

echo "==================================================================="
echo " BENCHMARK 2/2: escalamiento por PARTICIONES (Job cuentas anuales)"
echo "==================================================================="
for particiones in 1 2 4 8; do
  tag="particiones_${particiones}"
  dur=$(ejecutar "$tag" \
      --job=cuentas-anuales \
      --spring.profiles.active=demo \
      --spring.datasource.url="jdbc:h2:mem:bench_${tag};DB_CLOSE_DELAY=-1" \
      --app.archivos.cuentas-anuales="file:${DATA_DIR}/cuentas_anuales_grande.csv" \
      --app.batch.chunk-size=200 \
      --app.batch.particiones="${particiones}" \
      --app.reportes.directorio="${OUT_DIR}/${tag}")
  estado=$(cat "$OUT_DIR/estado_${tag}.txt")
  echo "cuentas-anuales,particiones,${particiones},${dur},${estado}" >> "$CSV_RESULTADOS"
done

echo "==================================================================="
echo " RESULTADOS (también guardados en ${CSV_RESULTADOS})"
echo "==================================================================="
column -s, -t "$CSV_RESULTADOS"
echo
echo "Revisa cuál combinación dio el menor 'duracion_ms' para justificar"
echo "la configuración óptima en tu informe/README."
