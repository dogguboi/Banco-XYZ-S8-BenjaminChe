package com.bancoxyz.bffweb.dto;

import com.bancoxyz.batch.model.EstadoCuentaAnual;
import com.bancoxyz.batch.model.MovimientoAnual;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CuentaWebDto(
        String cuentaId,
        String tipoCuenta,
        Integer edadTitular,
        BigDecimal saldoInicial,
        BigDecimal tasaInteres,
        BigDecimal interesCalculado,
        BigDecimal saldoFinal,
        LocalDate fechaCorte,
        EstadoCuentaAnual estadoAnual,
        List<MovimientoAnual> movimientos) {
}
