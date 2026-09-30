package com.bancoxyz.bffweb.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Indicadores agregados para el dashboard de la banca web. */
public record DashboardDto(
        long totalCuentas,
        BigDecimal saldoTotal,
        BigDecimal interesTotalCalculado,
        Map<String, Long> cuentasPorTipo,
        long totalTransacciones,
        long transaccionesAnomalas,
        long totalMovimientosAnuales) {
}
