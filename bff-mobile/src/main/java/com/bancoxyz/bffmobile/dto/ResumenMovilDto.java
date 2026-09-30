package com.bancoxyz.bffmobile.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resumen compacto para la pantalla de inicio de la app móvil. */
public record ResumenMovilDto(BigDecimal saldoTotal, long cuentas, List<UltimoMovimientoDto> ultimosMovimientos) {
    public record UltimoMovimientoDto(String fecha, String operacion, BigDecimal monto) {}
}
