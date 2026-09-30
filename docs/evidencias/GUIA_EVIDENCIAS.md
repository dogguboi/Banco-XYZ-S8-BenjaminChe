# Guía para generar la evidencia de ejecución (proyecto de continuidad)

Guarda cada captura en esta carpeta con el nombre indicado. Los certificados HTTPS son autofirmados: usa `-k` en curl.

## 0. Compilación

```bash
mvn clean install
```
📸 `00_build_success.png` — Reactor Summary con los **6 módulos** en SUCCESS y `BUILD SUCCESS`.

## 1. Poblar la base con los Jobs batch (semana 3)

Los BFFs exponen los datos que el batch sanea y persiste, así que primero se ejecutan los tres Jobs (perfil demo, H2 en `~/banco-xyz-data`):

```bash
./scripts/poblar-base.sh
# o desde IntelliJ: Run de BatchApplication con Program arguments: --job=transacciones (luego intereses y cuentas-anuales)
```
📸 `01_batch_poblado.png` — Consola de al menos un Job desde "INICIANDO JOB" hasta el bloque "RESUMEN JOB" con estado COMPLETED (sirven las mismas evidencias del estilo de la S3).

## 2. Arranque de los tres BFF (tres terminales o Run en IntelliJ)

```bash
./scripts/run-web.sh   # BffWebApplication    -> 8443
./scripts/run-mobile.sh # BffMobileApplication -> 8444
./scripts/run-atm.sh    # BffAtmApplication    -> 8445
```
📸 `02_arranque_web.png`, `03_arranque_mobile.png`, `04_arranque_atm.png` — cada consola con `Tomcat started on port 844x (https)`.

## 3. Autenticación por canal

```bash
curl -sk -X POST https://localhost:8443/auth/login -H "Content-Type: application/json" -d '{"username":"usuario.web","password":"web123"}'
curl -sk -X POST https://localhost:8444/auth/login -H "Content-Type: application/json" -d '{"username":"usuario.movil","password":"movil123"}'
curl -sk -X POST https://localhost:8445/auth/login -H "Content-Type: application/json" -d '{"username":"atm-001","password":"atm123"}'
```
📸 `05_login_web.png`, `06_login_mobile.png`, `07_login_atm.png` — respuesta con `accessToken` y `channel` de cada BFF.

Exporta los tokens: `TW=...  TM=...  TA=...`

## 4. APIs por canal

**Web:**
```bash
curl -sk https://localhost:8443/api/web/cuentas/101 -H "Authorization: Bearer $TW"
curl -sk https://localhost:8443/api/web/dashboard   -H "Authorization: Bearer $TW"
```
📸 `08_web_cuenta_completa.png` (JSON con saldos, tasa, interés, estadoAnual y movimientos) y `09_web_dashboard.png`.

**Móvil:**
```bash
curl -sk "https://localhost:8444/api/mobile/cuentas?page=0&size=3" -H "Authorization: Bearer $TM"
curl -sk https://localhost:8444/api/mobile/resumen -H "Authorization: Bearer $TM"
```
📸 `10_mobile_cuentas.png` (contraste con la respuesta web: solo 3 campos) y `11_mobile_resumen.png`.

**ATM (nota la persistencia real):**
```bash
curl -sk https://localhost:8445/api/atm/cuentas/101/saldo -H "Authorization: Bearer $TA"
curl -sk -X POST https://localhost:8445/api/atm/cuentas/101/retiro -H "Authorization: Bearer $TA" -H "Content-Type: application/json" -d '{"monto":500}'
curl -sk https://localhost:8445/api/atm/cuentas/101/saldo -H "Authorization: Bearer $TA"   # saldo ya rebajado
curl -sk -X POST https://localhost:8445/api/atm/cuentas/101/retiro -H "Authorization: Bearer $TA" -H "Content-Type: application/json" -d '{"monto":999999}'
```
📸 `12_atm_saldo_antes.png`, `13_atm_retiro_ok.png`, `14_atm_saldo_despues.png` (evidencia de persistencia), `15_atm_retiro_rechazado.png` (422 SUPERA_TOPE_2000).
📸 `16_atm_auditoria.png` — consola del BFF ATM con las líneas `ATM_AUDIT` (CONSULTA_SALDO, RETIRO_OK, RETIRO_RECHAZADO).
📸 Opcional pero potente: `17_bd_movimiento_retiro.png` — panel Database de IntelliJ sobre `~/banco-xyz-data/banco-xyz` mostrando la fila `retiro_atm` insertada en MOVIMIENTO_ANUAL.

## 5. Evidencia de seguridad

```bash
curl -sk -o /dev/null -w "%{http_code}\n" https://localhost:8443/api/web/cuentas                     # sin token
curl -sk -o /dev/null -w "%{http_code}\n" https://localhost:8445/api/atm/cuentas/101/saldo -H "Authorization: Bearer $TM"   # token móvil vs ATM
curl -sk -o /dev/null -w "%{http_code}\n" https://localhost:8443/api/web/dashboard -H "Authorization: Bearer $TA"           # token ATM vs Web
echo | openssl s_client -connect localhost:8443 2>/dev/null | openssl x509 -noout -subject -dates
```
📸 `18_seguridad_sin_token.png`, `19_aislamiento_canales.png`, `20_certificado_https.png` (también sirve el candado del navegador en https://localhost:8443).

## Alternativa rápida

`./scripts/smoke-test.sh` ejecuta las fases 3–5 completas; su salida capturada sirve como `99_smoke_test.png`.
