#!/usr/bin/env bash
# Smoke test del stack Exp3-S8 (requiere docker compose up y la base poblada)
set -u
AUTH=http://localhost:9000; WEB=https://localhost:8443; MOB=https://localhost:8444
ATM=https://localhost:8445; NOT=http://localhost:8090; CUENTA=${1:-ACC-1002}

token() { curl -s -u "$1:$2" -X POST "$AUTH/oauth2/token?grant_type=client_credentials&scope=$3" \
  | sed 's/.*"access_token":"\([^"]*\)".*/\1/'; }
titulo(){ echo; echo "============ $1 ============"; }

titulo "1. OAuth 2.0 - tokens por canal"
TW=$(token bff-web-client web-secret "web.read")
TM=$(token bff-mobile-client mobile-secret "mobile.read")
TA=$(token bff-atm-client atm-secret "atm.read%20atm.write")
echo "Token WEB   : ${TW:0:40}..."
echo "Token MOVIL : ${TM:0:40}..."
echo "Token ATM   : ${TA:0:40}..."

titulo "2. Consumo de los BFF (Resource Servers)"
echo "-- Dashboard web:"; curl -sk $WEB/api/web/dashboard -H "Authorization: Bearer $TW"; echo
echo "-- Cuentas movil:"; curl -sk "$MOB/api/mobile/cuentas?size=3" -H "Authorization: Bearer $TM"; echo

titulo "3. Retiro en cajero -> publica evento Kafka"
echo "-- Saldo antes:"; curl -sk $ATM/api/atm/cuentas/$CUENTA/saldo -H "Authorization: Bearer $TA"; echo
echo "-- Retiro 500:";  curl -sk -X POST $ATM/api/atm/cuentas/$CUENTA/retiro \
  -H "Authorization: Bearer $TA" -H "Content-Type: application/json" -d '{"monto":500}'; echo
echo "-- Saldo despues:"; curl -sk $ATM/api/atm/cuentas/$CUENTA/saldo -H "Authorization: Bearer $TA"; echo
sleep 2
echo "-- Eventos consumidos por notificaciones-service:"
curl -s $NOT/api/notificaciones; echo

titulo "4. Resilience4j - estado de los circuitos"
curl -sk $WEB/actuator/circuitbreakers; echo

titulo "5. Seguridad - autorizacion por scope"
echo -n "   Sin token                    -> HTTP "; curl -sk -o /dev/null -w "%{http_code}\n" $WEB/api/web/dashboard
echo -n "   Token MOVIL contra BFF Web   -> HTTP "; curl -sk -o /dev/null -w "%{http_code}\n" $WEB/api/web/dashboard -H "Authorization: Bearer $TM"
echo -n "   Token WEB contra BFF Cajeros -> HTTP "; curl -sk -o /dev/null -w "%{http_code}\n" $ATM/api/atm/cuentas/$CUENTA/saldo -H "Authorization: Bearer $TW"
echo; echo "Smoke test finalizado."
