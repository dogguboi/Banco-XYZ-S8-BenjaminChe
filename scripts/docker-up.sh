#!/usr/bin/env bash
# Levanta todo el ecosistema (Kafka + auth-server + 3 BFF + notificaciones)
cd "$(dirname "$0")/.." && docker compose up --build
