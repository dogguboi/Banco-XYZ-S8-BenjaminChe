package com.bancoxyz.notificaciones.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Copia del contrato del evento publicado por el BFF de cajeros.
 *
 * Se declara de forma independiente a propósito: en una arquitectura de
 * eventos, productor y consumidor comparten un CONTRATO, no código, de
 * modo que cada servicio pueda evolucionar por separado mientras respete
 * el formato del mensaje.
 */
public record RetiroEvent(
        String comprobante,
        String cuentaId,
        BigDecimal monto,
        BigDecimal saldoRestante,
        String operador,
        String canal,
        Instant fechaHora) {
}
