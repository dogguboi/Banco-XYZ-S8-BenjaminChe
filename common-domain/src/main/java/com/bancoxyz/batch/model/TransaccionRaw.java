package com.bancoxyz.batch.model;

import lombok.Data;

/**
 * Representa una fila cruda del archivo transacciones.csv.
 * Todos los campos se leen como String a propósito: así el
 * {@link org.springframework.batch.item.file.FlatFileItemReader} nunca falla
 * por un problema de tipos (fechas mal formateadas, montos vacíos, etc.).
 * La validación/conversión real ocurre en el ItemProcessor, donde SÍ podemos
 * aplicar políticas de skip/retry de forma controlada.
 */
@Data
public class TransaccionRaw {
    private String id;
    private String cuentaId;
    private String fecha;
    private String monto;
    private String tipoTransaccion;
    private String descripcion;
}
