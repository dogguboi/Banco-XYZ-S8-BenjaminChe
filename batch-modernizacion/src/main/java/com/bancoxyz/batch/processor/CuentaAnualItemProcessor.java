package com.bancoxyz.batch.processor;

import com.bancoxyz.batch.exception.RegistroInvalidoException;
import com.bancoxyz.batch.model.CuentaAnualRaw;
import com.bancoxyz.batch.model.MovimientoAnual;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CuentaAnualItemProcessor implements ItemProcessor<CuentaAnualRaw, MovimientoAnual> {

    private final Set<String> movimientosVistos = ConcurrentHashMap.newKeySet();

    @Override
    public MovimientoAnual process(CuentaAnualRaw raw) {

        if (raw.getCuentaId() == null || raw.getCuentaId().isBlank()) {
            throw new RegistroInvalidoException("Movimiento anual sin cuentaId: " + raw);
        }

        LocalDate fecha = ParseUtils.parseFechaFlexible(raw.getFecha());
        if (fecha == null) {
            throw new RegistroInvalidoException(
                    "Fecha inválida '" + raw.getFecha() + "' en movimiento de cuenta " + raw.getCuentaId());
        }

        BigDecimal monto = ParseUtils.parseMonto(raw.getMonto());
        if (monto == null) {
            throw new RegistroInvalidoException(
                    "Monto vacío o no numérico en movimiento de cuenta " + raw.getCuentaId());
        }
        if (monto.compareTo(BigDecimal.ZERO) == 0) {
            throw new RegistroInvalidoException(
                    "Monto en cero (sin efecto contable) en movimiento de cuenta " + raw.getCuentaId());
        }

        if (raw.getTipoOperacion() == null || raw.getTipoOperacion().isBlank()) {
            throw new RegistroInvalidoException(
                    "Tipo de operación faltante en movimiento de cuenta " + raw.getCuentaId());
        }

        String claveNatural = raw.getCuentaId().trim() + "|" + fecha + "|" + raw.getTipoOperacion().trim() + "|" + monto;
        if (!movimientosVistos.add(claveNatural)) {
            throw new RegistroInvalidoException("Movimiento anual duplicado: " + claveNatural);
        }

        // Descripción faltante: se corrige con un valor por defecto en lugar de descartar,
        // ya que no afecta la integridad contable del monto.
        String descripcion = (raw.getDescripcion() == null || raw.getDescripcion().isBlank())
                ? "SIN DESCRIPCION"
                : raw.getDescripcion().trim();

        return new MovimientoAnual(null, raw.getCuentaId().trim(), fecha,
                raw.getTipoOperacion().trim().toUpperCase(), monto, descripcion);
    }
}
