# Propuesta técnica — Desarrollando microservicios y resiliencia en la nube con Spring Cloud

**Asignatura:** Desarrollo Backend III (PBY2203) — Exp. 3, Semana 8
**Actividad:** Sumativa individual (continuidad del proyecto Banco XYZ)
**Estudiante:** Benjamín Che

---

## 1. Punto de partida y alcance

Este proyecto es la tercera iteración del sistema del Banco XYZ:

| Entrega | Aporte |
|---|---|
| **Exp2-S3** | Migración de los procesos batch legacy con Spring Batch: los CSV se sanean y persisten en Oracle Autonomous Database. |
| **Exp2-S5** | Patrón Backend for Frontend: tres backends independientes (Web, Móvil, Cajeros) sobre esa misma base, con HTTPS y JWT propio por canal. |
| **Exp3-S8 (esta entrega)** | El sistema se prepara para producción en la nube: identidad centralizada con **OAuth 2.0**, **tolerancia a fallos con Resilience4j**, **arquitectura orientada a eventos con Apache Kafka** y **contenerización con Docker y Docker Compose**. |

Los cinco componentes exigidos por la evaluación se implementan sobre la arquitectura ya existente, sin rehacerla: los BFF siguen siendo los mismos servicios, pero ahora delegan su seguridad, resisten fallos y publican eventos.

## 2. Arquitectura de la solución

```
                    ┌──────────────────────┐
                    │  auth-server :9000   │  OAuth 2.0 (Spring Authorization Server)
                    │  emite JWT RSA       │  /oauth2/token · /oauth2/jwks
                    └──────────┬───────────┘
                     valida firma (JWK Set)
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐
   │ bff-web     │      │ bff-mobile  │      │ bff-atm     │
   │ :8443 HTTPS │      │ :8444 HTTPS │      │ :8445 HTTPS │
   │ web.read    │      │ mobile.read │      │ atm.read/   │
   │             │      │             │      │ atm.write   │
   │ Resilience4j│      │ Resilience4j│      │ Resilience4j│
   └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
          └────────────────────┼────────────────────┘        │ publica evento
                               ▼                             ▼
              Oracle Autonomous Database              ┌──────────────┐
              (poblada por batch-modernizacion)       │ Kafka :9092  │
                                                      │ banco.retiros│
                                                      └──────┬───────┘
                                                             ▼
                                          ┌──────────────────────────────┐
                                          │ notificaciones-service :8090 │
                                          │ notifica + antifraude        │
                                          └──────────────────────────────┘
```

Todo el conjunto se levanta con un único `docker compose up --build`.

## 3. Criterio 1 — OAuth 2.0

### Decisión de diseño

En la semana 5 cada BFF emitía y validaba sus propios JWT con un secreto HMAC. Funcionaba, pero tenía tres debilidades propias de un sistema distribuido: el secreto de firma estaba replicado en tres servicios, cada BFF gestionaba usuarios y contraseñas, y agregar un cuarto canal obligaba a repetir toda esa lógica.

La solución es la que plantea la guía de la semana 8: **delegar la identidad a un servidor de autorización**. Se implementó un **Spring Authorization Server** (`auth-server`) que centraliza la emisión de tokens.

### Implementación

- **Flujo:** `client_credentials`. Es el flujo correcto para este caso, porque quien se autentica no es un usuario final navegando, sino **el frontend de cada canal como cliente confidencial del banco** (comunicación máquina a máquina). El cajero automático, por ejemplo, no tiene navegador donde mostrar una pantalla de consentimiento.
- **Un cliente OAuth2 por canal**, cada uno con sus propios scopes y vigencia:

| Cliente | Secret | Scopes | Vigencia del token |
|---|---|---|---|
| `bff-web-client` | `web-secret` | `web.read` | 30 min |
| `bff-mobile-client` | `mobile-secret` | `mobile.read` | 15 min |
| `bff-atm-client` | `atm-secret` | `atm.read`, `atm.write` | 5 min |

- **Firma asimétrica RSA**: el auth-server firma con su clave privada y publica la pública en `/oauth2/jwks`. Los BFF validan sin conocer ningún secreto — a diferencia del esquema HMAC anterior, un BFF comprometido no permite falsificar tokens.
- **Los tres BFF pasan a ser Resource Servers** (`spring-boot-starter-oauth2-resource-server`), configurados con el `jwk-set-uri` del auth-server.
- **Autorización por scope**: `/api/web/**` exige `SCOPE_web.read`, `/api/mobile/**` exige `SCOPE_mobile.read`. El aislamiento entre canales de la semana 5 se conserva, pero ahora expresado con un estándar de industria.
- **Grano fino en el canal crítico**: en cajeros, consultar saldo exige `atm.read` y ejecutar un retiro exige además `atm.write`, separando lectura de escritura.
- Se agregó un claim personalizado `canal` (WEB/MOBILE/ATM) para trazabilidad.

## 4. Criterio 4 — Tolerancia a fallos con Resilience4j

Se protegieron las operaciones que dependen de un recurso externo: la base de datos Oracle en la nube, que puede sufrir latencia o indisponibilidad.

**Patrones aplicados:**

| Patrón | Configuración | Propósito |
|---|---|---|
| **Retry** | 3 intentos, espera 500 ms con backoff exponencial ×2 | Absorbe fallos transitorios de red o conexión sin molestar al usuario. |
| **Circuit Breaker** | Ventana de 10 llamadas, mínimo 5, umbral 50 % de fallos, 20 s abierto, transición automática a half-open con 3 llamadas de prueba | Si la base deja de responder, deja de intentarlo y responde de inmediato, evitando el agotamiento de hilos y el efecto dominó. |
| **Fallback** | Método alternativo por operación | Devuelve una respuesta degradada (lista vacía, dashboard en cero) en lugar de un error 500. |

**Decisión importante — el retiro no tiene fallback.** Una operación que mueve dinero jamás debe "degradarse": si la base falla, la transacción se revierte y se informa el error. En el retiro Resilience4j aporta únicamente un **Retry acotado (2 intentos)** que excluye explícitamente las excepciones de negocio, porque un rechazo por saldo insuficiente o por superar el tope no debe reintentarse nunca. Esta distinción entre fallo técnico (reintentable) y fallo de negocio (definitivo) es el criterio que separa una implementación correcta de una peligrosa.

El estado de los circuitos es observable en `/actuator/circuitbreakers` y `/actuator/health`.

## 5. Criterio 5 — Mensajería asíncrona con Kafka

### El caso de negocio

Cuando un cliente retira dinero, el banco necesita además: notificarle, evaluar reglas antifraude y alimentar otros sistemas. Hacerlo de forma síncrona dentro del cajero sería un error de diseño: la entrega del efectivo quedaría supeditada a que el servicio de notificaciones responda.

### Implementación

- **Productor:** `bff-atm` publica un `RetiroEvent` en el tópico **`banco.retiros`** *después* de confirmarse la transacción bancaria. La clave de partición es la cuenta, de modo que los retiros de una misma cuenta conservan su orden.
- **Consumidor:** `notificaciones-service`, un microservicio independiente, se suscribe al tópico y por cada evento envía la notificación al cliente y evalúa la regla antifraude (retiros ≥ $1.000 generan alerta). Expone `/api/notificaciones` para consultar los eventos procesados.
- **Contrato, no código compartido:** el consumidor declara su propia copia del record del evento. Productor y consumidor comparten el formato del mensaje, no una librería común, para que puedan evolucionar por separado.
- **Fallo tolerado:** si Kafka no está disponible, la publicación se registra como error pero **no revierte el retiro** (`max.block.ms: 5000` evita que el cajero se bloquee). El dinero ya fue entregado; perder la notificación es preferible a bloquear la operación.
- **Desacoplamiento real:** el microservicio de notificaciones puede caerse por completo sin que los cajeros dejen de operar; al reiniciarse, recupera desde Kafka los eventos pendientes (`auto-offset-reset: earliest`).

## 6. Criterios 2 y 3 — Docker y Docker Compose

### Imágenes

Cada uno de los cinco servicios tiene su **Dockerfile multi-etapa**:
1. **Etapa de compilación** (`maven:3.9-eclipse-temurin-17`): copia primero los POM para aprovechar la caché de dependencias de Docker, luego el código, y compila solo ese módulo con `-pl <modulo> -am`.
2. **Etapa de ejecución** (`eclipse-temurin:17-jre`): contiene solo el JRE y el jar. La imagen final es considerablemente más liviana y no incluye Maven, el código fuente ni el repositorio de dependencias.

Como buena práctica de seguridad, el contenedor **no corre como root**: se crea el usuario `banco` sin privilegios.

### Orquestación

`docker-compose.yaml` levanta siete servicios en una red propia (`banco-xyz-net`): Kafka (modo KRaft, sin Zookeeper), Kafka UI, auth-server, los tres BFF y el servicio de notificaciones.

Aspectos relevantes de la orquestación:
- **Healthchecks y `depends_on: condition: service_healthy`**: los BFF no arrancan hasta que el auth-server responde su JWK Set, y el productor/consumidor esperan a que Kafka esté operativo. Evita los fallos de arranque en cascada.
- **`restart: on-failure`**: reinicio automático de contenedores caídos, la estrategia de resiliencia a nivel de infraestructura que menciona la guía.
- **Configuración externalizada** por variables de entorno y un archivo `.env` (no versionado): el perfil de base de datos, las credenciales y la ruta del Wallet.
- **Wallet de Oracle montado como volumen de solo lectura** en `/wallet`: los contenedores se conectan a la misma Oracle Autonomous Database que puebla el módulo batch, sin que las credenciales queden dentro de la imagen.
- **Descubrimiento por DNS de Docker**: los servicios se referencian por nombre (`kafka:9092`, `auth-server:9000`), sin IPs fijas.

## 7. Seguridad: resumen de capas

1. **HTTPS/TLS** en los tres BFF (heredado de la S5, keystore PKCS12 por canal).
2. **OAuth 2.0** con tokens JWT firmados en RSA por un servidor de autorización centralizado.
3. **Autorización por scope**, con separación lectura/escritura en el canal de cajeros.
4. **Secretos fuera del código**: variables de entorno y `.env` excluido del repositorio, igual que el Wallet de Oracle.
5. **Contenedores sin privilegios de root**.

## 8. Trabajo futuro

- Incorporar Eureka y Spring Cloud Gateway para descubrimiento y punto de entrada único (no exigidos por esta evaluación, pero coherentes con la arquitectura de referencia de la guía).
- Persistir los eventos consumidos y aplicar el patrón Saga para operaciones que abarquen varios servicios.
- Desplegar las imágenes en un registro y orquestarlas en Kubernetes con escalado horizontal.
