#!/usr/bin/env bash
#
# Genera datasets sintéticos MÁS GRANDES que el dataset de demo (que es
# intencionalmente pequeño y "sucio" para evidenciar el manejo de errores).
#
# El objetivo de este script es tener volumen suficiente para que las
# distintas configuraciones de escalamiento (hilos / particiones) muestren
# una diferencia de tiempo medible, algo que con 10-15 filas no ocurre.
#
# Uso:
#   ./scripts/generar_dataset_benchmark.sh [cantidad_filas]
#
# Salida:
#   benchmark-data/transacciones_grande.csv
#   benchmark-data/cuentas_anuales_grande.csv
#
set -euo pipefail

CANTIDAD="${1:-20000}"
DIR_SALIDA="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/benchmark-data"
mkdir -p "$DIR_SALIDA"

echo "Generando ${CANTIDAD} filas sintéticas en ${DIR_SALIDA} ..."

# --- transacciones_grande.csv ---
{
  echo "id,cuentaId,fecha,monto,tipoTransaccion,descripcion"
  awk -v n="$CANTIDAD" 'BEGIN {
    srand(42);
    tipos[0]="DEPOSITO"; tipos[1]="RETIRO"; tipos[2]="TRANSFERENCIA"; tipos[3]="PAGO";
    for (i = 1; i <= n; i++) {
      dia = 1 + int(rand() * 27);
      mes = 1 + int(rand() * 12);
      monto = 1000 + int(rand() * 500000);
      tipo = tipos[int(rand() * 4)];
      printf "%d,ACC-%05d,2024-%02d-%02d,%d,%s,Transaccion sintetica %d\n", i, (i % 500), mes, dia, monto, tipo, i;
    }
  }'
} > "$DIR_SALIDA/transacciones_grande.csv"

# --- cuentas_anuales_grande.csv ---
{
  echo "cuentaId,fecha,tipoOperacion,monto,descripcion"
  awk -v n="$CANTIDAD" 'BEGIN {
    srand(7);
    tipos[0]="DEPOSITO"; tipos[1]="RETIRO"; tipos[2]="TRANSFERENCIA"; tipos[3]="PAGO";
    for (i = 1; i <= n; i++) {
      dia = 1 + int(rand() * 27);
      mes = 1 + int(rand() * 12);
      monto = 1000 + int(rand() * 300000);
      tipo = tipos[int(rand() * 4)];
      printf "ACC-%05d,2024-%02d-%02d,%s,%d,Movimiento sintetico %d\n", (i % 500), mes, dia, tipo, monto, i;
    }
  }'
} > "$DIR_SALIDA/cuentas_anuales_grande.csv"

echo "Listo:"
wc -l "$DIR_SALIDA/transacciones_grande.csv" "$DIR_SALIDA/cuentas_anuales_grande.csv"
