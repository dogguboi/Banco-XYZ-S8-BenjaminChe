package com.bancoxyz.bffatm.dto;

import java.math.BigDecimal;

/** Respuesta mínima de consulta de saldo en cajero. */
public record SaldoDto(String cuentaId, BigDecimal saldo, String moneda) {
    public static SaldoDto clp(String cuentaId, BigDecimal saldo) {
        return new SaldoDto(cuentaId, saldo, "CLP");
    }
}
