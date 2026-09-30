package com.bancoxyz.batch.processor;

import com.bancoxyz.batch.exception.RegistroInvalidoException;
import com.bancoxyz.batch.model.CuentaInteres;
import com.bancoxyz.batch.model.InteresRaw;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class InteresItemProcessor implements ItemProcessor<InteresRaw, CuentaInteres> {

    private static final int EDAD_MIN = 18;
    private static final int EDAD_MAX = 110;

    private final Set<String> clavesProcesadas = ConcurrentHashMap.newKeySet();

    @Override
    public CuentaInteres process(InteresRaw raw) {

        if (raw.getCuentaId() == null || raw.getCuentaId().isBlank()) {
            throw new RegistroInvalidoException("Registro de interés sin cuentaId: " + raw);
        }

        LocalDate fecha = ParseUtils.parseFechaFlexible(raw.getFecha());
        if (fecha == null) {
            throw new RegistroInvalidoException(
                    "Fecha de corte inválida '" + raw.getFecha() + "' para cuenta " + raw.getCuentaId());
        }

        String claveNatural = raw.getCuentaId().trim() + "|" + fecha;
        if (!clavesProcesadas.add(claveNatural)) {
            throw new RegistroInvalidoException("Registro de interés duplicado para " + claveNatural);
        }

        CuentaInteres.TipoCuenta tipoCuenta;
        try {
            tipoCuenta = CuentaInteres.TipoCuenta.valueOf(raw.getTipoCuenta().trim().toUpperCase());
        } catch (Exception e) {
            throw new RegistroInvalidoException(
                    "Tipo de cuenta inválido '" + raw.getTipoCuenta() + "' en cuenta " + raw.getCuentaId());
        }

        BigDecimal saldo = ParseUtils.parseMonto(raw.getSaldo());
        if (saldo == null) {
            throw new RegistroInvalidoException(
                    "Saldo vacío o no numérico en cuenta " + raw.getCuentaId());
        }
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegistroInvalidoException(
                    "Saldo negativo inconsistente en cuenta " + raw.getCuentaId());
        }

        BigDecimal tasa = ParseUtils.parseMonto(raw.getTasaInteres());
        if (tasa == null || tasa.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegistroInvalidoException(
                    "Tasa de interés inválida en cuenta " + raw.getCuentaId());
        }

        Integer edad = ParseUtils.parseEntero(raw.getEdadTitular());
        if (edad == null || edad < EDAD_MIN || edad > EDAD_MAX) {
            throw new RegistroInvalidoException(
                    "Edad de titular fuera de rango (" + raw.getEdadTitular() + ") en cuenta " + raw.getCuentaId());
        }

        // Interés simple mensual: saldo * (tasa% / 100)
        BigDecimal interes = saldo.multiply(tasa)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal saldoFinal = saldo.add(interes);

        return new CuentaInteres(null, raw.getCuentaId().trim(), tipoCuenta, saldo, tasa, edad,
                fecha, interes, saldoFinal);
    }
}
