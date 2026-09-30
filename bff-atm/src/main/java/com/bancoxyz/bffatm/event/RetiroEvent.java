package com.bancoxyz.bffatm.event;

import java.math.BigDecimal;
import java.time.Instant;

public record RetiroEvent(
        String comprobante,
        String cuentaId,
        BigDecimal monto,
        BigDecimal saldoRestante,
        String operador,
        String canal,
        Instant fechaHora) {

    public static RetiroEvent de(String comprobante, String cuentaId, BigDecimal monto,
                                 BigDecimal saldoRestante, String operador) {
        return new RetiroEvent(comprobante, cuentaId, monto, saldoRestante,
                operador, "ATM", Instant.now());
    }
}
