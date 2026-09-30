package com.bancoxyz.bffatm.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Comprobante de un retiro realizado en cajero. */
public record RetiroResponse(
        String comprobante,
        String cuentaId,
        BigDecimal montoRetirado,
        BigDecimal saldoRestante,
        Instant fechaHora) {
}
