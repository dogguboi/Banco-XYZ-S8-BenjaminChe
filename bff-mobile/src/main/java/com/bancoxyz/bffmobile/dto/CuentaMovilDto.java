package com.bancoxyz.bffmobile.dto;

import java.math.BigDecimal;

public record CuentaMovilDto(String cuentaId, String tipo, BigDecimal saldo) {
}
