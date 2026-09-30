# =====================================================================
# Microservicio consumidor de eventos Kafka - Banco XYZ (Exp3-S8)
# Imagen multi-etapa: se compila con Maven y se publica solo el JRE
# con el jar, para obtener una imagen mas liviana y segura.
# =====================================================================

# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copiar primero los POM para aprovechar la cache de dependencias
COPY pom.xml .
COPY common-domain/pom.xml common-domain/
COPY auth-server/pom.xml auth-server/
COPY bff-web/pom.xml bff-web/
COPY bff-mobile/pom.xml bff-mobile/
COPY bff-atm/pom.xml bff-atm/
COPY notificaciones-service/pom.xml notificaciones-service/
COPY batch-modernizacion/pom.xml batch-modernizacion/

# Codigo fuente de este servicio y de sus modulos comunes
COPY common-domain/src common-domain/src
COPY notificaciones-service/src notificaciones-service/src

# Compilar solo este modulo y los que necesita (-am), sin tests
RUN mvn -q -pl notificaciones-service -am -DskipTests package

# ---------- Etapa 2: ejecucion ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl: requerido por los healthcheck de docker-compose y util para
# diagnosticar la conectividad desde dentro del contenedor
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Usuario sin privilegios (buena practica de seguridad en contenedores)
RUN groupadd -r banco && useradd -r -g banco banco

COPY --from=build /build/notificaciones-service/target/*.jar app.jar
RUN chown -R banco:banco /app
USER banco

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
