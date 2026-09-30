# Guía para generar la evidencia de ejecución — Exp3-S8

Las capturas cubren los cinco criterios de la pauta. Guárdalas en esta carpeta con el nombre indicado.
Los BFF usan HTTPS autofirmado: en Postman desactiva **Settings → SSL certificate verification**.

## Preparación

1. Copia `.env.example` como `.env` y completa la ruta del Wallet y la contraseña de Oracle.
2. Asegúrate de que la base esté poblada por los Jobs del módulo batch (o usa `DB_PROFILE=demo`).
3. Importa en Postman `postman/BE3_Semana8_BancoXYZ_OAuth2.postman_collection.json`.

---

## Criterio 2 y 3 — Docker y docker-compose

```bash
docker compose build          # o directamente el up de abajo
docker compose up --build
```
📸 `01_build_imagenes.png` — Consola del build mostrando la construcción de las 5 imágenes (etapas Maven y JRE).
📸 `02_compose_up.png` — Arranque del stack: los logs entrelazados de kafka, auth-server, los 3 BFF y notificaciones.

```bash
docker compose ps
docker images | findstr bancoxyz     # en Linux/Mac: docker images | grep bancoxyz
```
📸 `03_compose_ps.png` — Los 7 contenedores en estado `running` / `healthy`.
📸 `04_docker_images.png` — Las 5 imágenes `bancoxyz/*:1.0.0` creadas, con su tamaño.

---

## Criterio 1 — OAuth 2.0

En Postman, carpeta **1. OAuth 2.0 - Authorization Server**:

📸 `05_jwks.png` — `GET /oauth2/jwks`: la clave pública RSA que publica el auth-server (tests verdes).
📸 `06_token_web.png` — Token del canal Web: `access_token`, `token_type: Bearer`, `scope: web.read`, `expires_in: 1800`.
📸 `07_token_movil.png` — Token del canal Móvil (`mobile.read`, 900 s).
📸 `08_token_atm.png` — Token del canal Cajeros (`atm.read atm.write`, 300 s).
📸 `09_credenciales_invalidas.png` — Cliente con secret incorrecto: rechazado con 401.

**Opcional muy recomendable:** pega el `access_token` en https://jwt.io y captura el payload decodificado,
donde se ven los claims `iss`, `aud`, `scope`, `exp` y el claim personalizado `canal`.
📸 `10_token_decodificado.png`

Consumo de los BFF como Resource Servers:
📸 `11_web_dashboard.png` — `GET /api/web/dashboard` con el token web: 200 OK.
📸 `12_mobile_cuentas.png` — `GET /api/mobile/cuentas?size=3`: 200 OK, DTOs mínimos.

Pruebas negativas (carpeta **6. Seguridad**): el status sale en rojo (401/403) y el **test en verde**.
📸 `13_sin_token.png` — Petición sin token → 401.
📸 `14_scope_insuficiente.png` — Token del canal Móvil contra el BFF Web → 403 por scope.
📸 `15_token_alterado.png` — Token con firma inválida → 401.

---

## Criterio 4 — Resilience4j

Estado normal:
```
GET https://localhost:8443/actuator/circuitbreakers
GET https://localhost:8443/actuator/health
```
📸 `16_circuitbreaker_closed.png` — Circuito `bancoDatos` en estado **CLOSED**, con sus métricas.

**Provocar la apertura del circuito** (la evidencia más potente del criterio). Con el stack arriba,
corta la conexión a la base y llama varias veces al endpoint:

```bash
# Opción A: desconectar el contenedor de la red (simula caída de la base)
docker network disconnect banco-xyz-net banco-bff-web

# Llamar 6+ veces al dashboard desde Postman (o con curl) y volver a consultar el actuator
docker network connect banco-xyz-net banco-bff-web   # restaurar al terminar
```

*Alternativa más simple*: en Oracle Cloud detén ("Stop") la Autonomous Database unos minutos, o
cambia temporalmente `TNS_ALIAS` en `.env` por un alias inexistente y levanta solo el BFF web.

📸 `17_fallback_log.png` — Logs del contenedor (`docker logs banco-bff-web`) con las líneas
`[FALLBACK] dashboard: la base no respondió...` y los reintentos.
📸 `18_circuitbreaker_open.png` — `/actuator/circuitbreakers` con el estado **OPEN**.
📸 `19_respuesta_degradada.png` — El dashboard respondiendo **200 con indicadores en cero**
en lugar de un error 500: el canal sigue en pie.

---

## Criterio 5 — Kafka y arquitectura de eventos

Carpeta **4. BFF Cajeros** de Postman, en orden:

📸 `20_saldo_antes.png` — Saldo previo.
📸 `21_retiro_ok.png` — Retiro de 500: comprobante y saldo restante.
📸 `22_saldo_despues.png` — Saldo rebajado (persistencia en Oracle).
📸 `23_log_productor.png` — `docker logs banco-bff-atm`: línea
`[KAFKA] Evento publicado en banco.retiros particion=0 offset=0 comprobante=XXXX`.
📸 `24_log_consumidor.png` — `docker logs banco-notificaciones`: `EVENTO RECIBIDO #1 ...`,
`NOTIFICACION ENVIADA -> cuenta ACC-1002: ...`.

Alerta antifraude (retiro de 1500):
📸 `25_alerta_antifraude.png` — Log del consumidor con `ALERTA ANTIFRAUDE: retiro de monto elevado...`.

Evidencia visual del tópico — abre **Kafka UI en http://localhost:8081**:
📸 `26_kafka_ui_topico.png` — El tópico `banco.retiros` con su número de mensajes.
📸 `27_kafka_ui_mensaje.png` — El contenido JSON de un mensaje (comprobante, cuenta, monto, saldoRestante).

API del consumidor:
📸 `28_api_notificaciones.png` — `GET http://localhost:8090/api/notificaciones`: eventos procesados y notificaciones generadas.

**Prueba de desacoplamiento (excelente para la defensa):**
```bash
docker compose stop notificaciones-service   # el consumidor cae
# ejecutar un retiro en Postman -> funciona igual (200 OK)
docker compose start notificaciones-service  # al volver, consume el evento pendiente
```
📸 `29_desacoplamiento.png` — El retiro exitoso con el consumidor apagado, y el log del consumidor
procesando el evento al reiniciarse.

---

## Criterio 6 — Entregables

- Código fuente en GitHub (con `docs/propuesta_tecnica_S8.md`).
- `README.md` con objetivo, estructura e instrucciones.
- Esta carpeta con las capturas.

## Atajo

Con el stack levantado, la colección de Postman permite ejecutar de corrido los seis grupos de peticiones
y capturar cada resultado con sus tests en verde.
