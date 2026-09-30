package com.bancoxyz.batch.processor;

import com.bancoxyz.batch.exception.ErrorTransitorioException;
import com.bancoxyz.batch.exception.RegistroInvalidoException;
import com.bancoxyz.batch.model.TransaccionDiaria;
import com.bancoxyz.batch.model.TransaccionRaw;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TransaccionItemProcessor implements ItemProcessor<TransaccionRaw, TransaccionDiaria> {

    @Value("${app.batch.umbral-anomalia:300000}")
    private BigDecimal umbralAnomalia;

    /** Claves naturales ya vistas, para detectar duplicados entre threads. */
    private final Set<String> idsProcesados = ConcurrentHashMap.newKeySet();

    /** Simula errores transitorios (ej. timeout de red) para demostrar el retry. */
    private final ConcurrentHashMap<String, Integer> intentosPorRegistro = new ConcurrentHashMap<>();

    @Override
    public TransaccionDiaria process(TransaccionRaw raw) {

        // 1) Duplicados: misma clave natural (id) ya procesada.
        if (raw.getId() == null || raw.getId().isBlank()) {
            throw new RegistroInvalidoException("Transacción sin id: " + raw);
        }
        if (!idsProcesados.add(raw.getId())) {
            throw new RegistroInvalidoException("Transacción duplicada, id=" + raw.getId());
        }

        // 2) Simulación de error transitorio controlado (solo demo de retry):
        //    la primera vez que se ve un registro "marcado", falla; la segunda
        //    vez (reintento) se procesa con normalidad.
        if ("6".equals(raw.getId())) {
            int intento = intentosPorRegistro.merge(raw.getId(), 1, Integer::sum);
            if (intento == 1) {
                log.warn("Simulando error transitorio en registro id={} (intento {})", raw.getId(), intento);
                throw new ErrorTransitorioException("Timeout simulado de conexión al validar id=" + raw.getId());
            }
        }

        // 3) Cuenta obligatoria
        if (raw.getCuentaId() == null || raw.getCuentaId().isBlank()) {
            throw new RegistroInvalidoException("Transacción sin cuentaId, id=" + raw.getId());
        }

        // 4) Fecha: se intenta corregir formato; si no se puede interpretar, se descarta.
        LocalDate fecha = ParseUtils.parseFechaFlexible(raw.getFecha());
        if (fecha == null) {
            throw new RegistroInvalidoException(
                    "Fecha inválida '" + raw.getFecha() + "' en transacción id=" + raw.getId());
        }

        // 5) Monto: debe ser numérico. Negativo o cero => anomalía (no se descarta).
        BigDecimal monto = ParseUtils.parseMonto(raw.getMonto());
        if (monto == null) {
            throw new RegistroInvalidoException(
                    "Monto no numérico o vacío en transacción id=" + raw.getId());
        }

        // 6) Tipo de transacción: debe pertenecer al catálogo permitido.
        TransaccionDiaria.TipoTransaccion tipo;
        try {
            tipo = TransaccionDiaria.TipoTransaccion.valueOf(raw.getTipoTransaccion().trim().toUpperCase());
        } catch (Exception e) {
            throw new RegistroInvalidoException(
                    "Tipo de transacción inválido '" + raw.getTipoTransaccion() + "' en id=" + raw.getId());
        }

        boolean esAnomalia = monto.compareTo(BigDecimal.ZERO) <= 0
                || monto.compareTo(umbralAnomalia) > 0;

        String descripcion = (raw.getDescripcion() == null || raw.getDescripcion().isBlank())
                ? "SIN DESCRIPCION"
                : raw.getDescripcion().trim();

        return new TransaccionDiaria(null, raw.getCuentaId().trim(), fecha, monto, tipo, descripcion, esAnomalia);
    }
}
