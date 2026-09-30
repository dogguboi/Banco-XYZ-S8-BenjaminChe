#!/usr/bin/env bash
# Smoke test de los tres BFF del Banco XYZ (requiere base poblada y BFFs arriba).
set -u
token() { curl -sk -X POST "$1/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$2\",\"password\":\"$3\"}" \
    | sed 's/.*"accessToken":"\([^"]*\)".*/\1/'; }
titulo() { echo; echo "==================== $1 ===================="; }

WEB=https://localhost:8443; MOB=https://localhost:8444; ATM=https://localhost:8445

titulo "1. BFF WEB (8443) - datos completos desde la BD del batch"
TW=$(token $WEB usuario.web web123)
echo "-- Token WEB: ${TW:0:40}..."
echo "-- GET /api/web/dashboard"
curl -sk $WEB/api/web/dashboard -H "Authorization: Bearer $TW"; echo
echo "-- GET /api/web/cuentas/101 (detalle completo)"
curl -sk $WEB/api/web/cuentas/101 -H "Authorization: Bearer $TW"; echo

titulo "2. BFF MOVIL (8444) - respuestas ligeras"
TM=$(token $MOB usuario.movil movil123)
echo "-- Token MOBILE: ${TM:0:40}..."
echo "-- GET /api/mobile/cuentas?page=0&size=3"
curl -sk "$MOB/api/mobile/cuentas?page=0&size=3" -H "Authorization: Bearer $TM"; echo
echo "-- GET /api/mobile/resumen"
curl -sk $MOB/api/mobile/resumen -H "Authorization: Bearer $TM"; echo
echo "-- Comparacion de tamano de respuesta:"
echo "   /api/web/cuentas    -> $(curl -sk $WEB/api/web/cuentas -H "Authorization: Bearer $TW" | wc -c) bytes"
echo "   /api/mobile/cuentas -> $(curl -sk "$MOB/api/mobile/cuentas?size=5" -H "Authorization: Bearer $TM" | wc -c) bytes"

titulo "3. BFF ATM (8445) - operaciones criticas con persistencia"
TA=$(token $ATM atm-001 atm123)
echo "-- Token ATM: ${TA:0:40}..."
echo "-- Saldo ANTES del retiro:"
curl -sk $ATM/api/atm/cuentas/101/saldo -H "Authorization: Bearer $TA"; echo
echo "-- POST retiro {monto: 500}"
curl -sk -X POST $ATM/api/atm/cuentas/101/retiro -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" -d '{"monto":500}'; echo
echo "-- Saldo DESPUES del retiro (persistido en la BD):"
curl -sk $ATM/api/atm/cuentas/101/saldo -H "Authorization: Bearer $TA"; echo
echo "-- Retiro invalido (999999, supera tope) -> 422 esperado"
curl -sk -X POST $ATM/api/atm/cuentas/101/retiro -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" -d '{"monto":999999}'; echo

titulo "4. AISLAMIENTO DE SEGURIDAD ENTRE CANALES"
echo "-- Sin token al BFF Web -> 401/403 esperado"
curl -sk -o /dev/null -w "   HTTP %{http_code}\n" $WEB/api/web/cuentas
echo "-- Token MOVIL contra BFF ATM -> 401/403 esperado"
curl -sk -o /dev/null -w "   HTTP %{http_code}\n" $ATM/api/atm/cuentas/101/saldo -H "Authorization: Bearer $TM"
echo "-- Token ATM contra BFF Web -> 401/403 esperado"
curl -sk -o /dev/null -w "   HTTP %{http_code}\n" $WEB/api/web/dashboard -H "Authorization: Bearer $TA"

titulo "5. HTTPS"
echo | openssl s_client -connect localhost:8443 2>/dev/null | openssl x509 -noout -subject -issuer -dates
echo; echo "Smoke test finalizado."
