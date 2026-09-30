# Propuesta técnica — Implementando el patrón arquitectónico Backend for Frontend (BFF)

**Asignatura:** Desarrollo Backend III (PBY2203) — Exp. 2, Semana 5
**Actividad:** Sumativa individual (continuidad del proyecto Banco XYZ)
**Estudiante:** Benjamín Che

---

## 1. Contexto de continuidad

Este proyecto es la continuación directa del trabajo de la semana 3, en el que los procesos batch legacy del Banco XYZ fueron migrados a Spring Batch. Aquellos Jobs leen los CSV legacy (con montos inválidos, fechas en formatos mixtos, duplicados y campos vacíos), los sanean mediante ItemProcessors y políticas de skip/retry, y persisten el resultado en una base de datos relacional en las tablas `cuenta_interes`, `transaccion_diaria`, `movimiento_anual` y `estado_cuenta_anual`.

La semana 5 agrega la capa que faltaba: **cómo exponen esos datos los distintos frontends del banco**. En lugar de un único backend genérico, se implementa el patrón Backend for Frontend con un backend dedicado por canal (web, móvil y cajeros), todos leyendo la misma base de datos que el batch pobló. La integridad y consistencia de los datos queda así garantizada por diseño: los BFFs solo exponen datos que ya pasaron por el saneamiento del batch, y las operaciones de escritura del canal de cajeros se ejecutan en transacciones que actualizan el saldo y registran el movimiento de forma atómica.

## 2. Análisis de la estrategia de implementación

Siguiendo la guía de la semana 5, se evaluaron las tres estrategias contra los antecedentes del caso:

| Estrategia | Evaluación frente al caso Banco XYZ | Veredicto |
|---|---|---|
| **1. Backends independientes por tipo de cliente** | Hay reglas de negocio distintas por cliente (retiros solo en cajeros, agregados solo en web), perfiles de demanda/disponibilidad diferentes y una necesidad crítica de aislar la seguridad de cada canal, propia de un banco. Todas las preguntas de decisión de la guía se responden afirmativamente. | **Seleccionada** |
| **2. Endpoints personalizados** | Más simple, pero no aísla seguridad ni despliegue: una falla del canal web afectaría a los cajeros, y no permite escalar por canal. | Descartada |
| **3. Delegación con microservicios** | El banco no posee aún microservicios; su backend de datos es el módulo batch + base relacional. Adoptarla ahora agrega complejidad sin beneficio. | Descartada (viable a futuro) |

**Decisión:** Estrategia 1, materializada como un proyecto Maven multi-módulo donde `bff-web`, `bff-mobile` y `bff-atm` son tres aplicaciones Spring Boot independientes (puerto, certificado HTTPS, secreto JWT, usuarios y reglas propios; despliegue y escalado separados). La principal desventaja de la estrategia —duplicación de esfuerzos— se mitiga con dos módulos comunes:

- `common-domain`: entidades JPA y repositorios extraídos del proyecto batch **conservando sus paquetes originales** (`com.bancoxyz.batch.model` / `.repository`), de modo que el código de la semana 3 no requirió ni una línea de modificación y ambas capas comparten exactamente el mismo modelo.
- `common-security`: emisión/validación de JWT (HS256) con secreto y canal (`aud`) por BFF, filtro de autenticación y login por canal con BCrypt.

## 3. Arquitectura de la solución

```
CSV legacy ──▶ batch-modernizacion (Jobs: sanea y persiste)
                        ▼
     H2 compartida ~/banco-xyz-data (AUTO_SERVER: multi-JVM)
                        ▼
   BFF Web :8443 · BFF Móvil :8444 · BFF ATM :8445
```

El único ajuste realizado al módulo batch fue la URL de la base H2, que pasó de una ruta relativa (`./data/...`) a una ruta fija en el home del usuario (`~/banco-xyz-data/banco-xyz;AUTO_SERVER=TRUE`). Esto permite que el batch y los tres BFFs —procesos JVM distintos, con directorios de trabajo distintos— compartan el mismo archivo de base de datos, y `AUTO_SERVER` habilita las conexiones simultáneas.

### Personalización por canal

| Aspecto | BFF Web (8443) | BFF Móvil (8444) | BFF ATM (8445) |
|---|---|---|---|
| DTO de cuenta | Completo: saldos, tasa, interés, edad, estado anual compilado e historial de movimientos | Ligero: cuentaId, tipo y saldo | Mínimo: cuentaId, saldo y moneda |
| Endpoints | /api/web/cuentas, /cuentas/{id}, /transacciones, /dashboard | /api/mobile/cuentas (paginado, máx. 5), /cuentas/{id}, /resumen | /api/atm/cuentas/{id}/saldo, /cuentas/{id}/retiro |
| Optimización | Agregados precalculados (saldo total, interés total, cuentas por tipo, conteo de anomalías) | Paginación tope 5 + compresión gzip + campos mínimos | Superficie mínima de API, sin listados masivos |
| Vida del token | 30 min | 15 min | 5 min |
| Escritura | Solo lectura | Solo lectura | Retiro transaccional: actualiza `cuenta_interes` y registra el movimiento en `movimiento_anual` |

### Seguridad (HTTPS, autenticación y autorización)

1. **HTTPS en los tres BFFs**: keystore PKCS12 propio por canal (certificado autofirmado para el entorno académico) y `requiresChannel().requiresSecure()`; el canal ATM agrega HSTS.
2. **Autenticación por canal**: cada BFF expone su `POST /auth/login` con usuarios propios (BCrypt) y emite JWT firmados con un **secreto exclusivo** del canal. En cajeros, cada equipo físico (atm-001, atm-002) tiene credencial propia.
3. **Autorización y aislamiento**: el filtro JWT valida firma, expiración y el claim `aud` (WEB/MOBILE/ATM); las rutas exigen el rol del canal y todo lo no declarado se deniega (`denyAll`). Un token del canal móvil es rechazado por el BFF web y el de cajeros, y viceversa.
4. **Auditoría del canal crítico**: toda consulta de saldo y todo retiro (exitoso o rechazado) queda en el log `ATM_AUDIT`, y los retiros exitosos quedan además persistidos como movimientos en la base.

### Integridad y consistencia

- El saneamiento de los datos legacy es el ya implementado y probado en la semana 3 (skip de montos inválidos y duplicados, normalización de fechas `yyyy/MM/dd`, catálogos de tipos, límites configurables), por lo que los BFFs exponen únicamente datos limpios.
- El retiro del cajero valida monto positivo, múltiplo de $10, tope de $2.000 por operación y saldo suficiente, y ejecuta el débito y el registro de auditoría dentro de una única transacción (`@Transactional`): si algo falla, todo se revierte.
- El "saldo actual" de una cuenta se define como el `saldo_final` del corte mensual más reciente calculado por el Job de intereses (o el `saldo_inicial` si aún no hay interés calculado), documentando una regla de negocio única para los tres canales.

## 4. Organización del código y escalabilidad

Agregar un cuarto canal (por ejemplo corresponsalías o IoT) equivale a crear un nuevo módulo `bff-<canal>` que reutiliza `common-domain` y `common-security`, sin tocar los canales existentes ni el batch. Los tres BFFs incorporan los mismos perfiles de base de datos que el módulo batch (`demo` con H2 por defecto, `oracle` con Wallet de Oracle Cloud, `mysql` y `postgres`), por lo que cambiar de motor es solo activar un perfil, sin cambios de código: el mismo mecanismo con el que la semana 3 se evidenció contra Oracle Autonomous Database.

## 5. Trabajo futuro

Evolucionar a la estrategia de delegación cuando existan microservicios; certificados de una CA y secretos JWT en un gestor con rotación; y programar los Jobs batch (scheduler) para refrescar la base que consumen los BFFs de forma periódica.
