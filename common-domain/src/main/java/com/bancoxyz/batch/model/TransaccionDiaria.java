package com.bancoxyz.batch.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entidad de destino para el Job "Reporte de Transacciones Diarias".
 * Incluye banderas de anomalía para que el resumen final (paso de reporte)
 * pueda informar cuántas transacciones fueron marcadas como sospechosas.
 */
@Entity
@Table(name = "transaccion_diaria")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionDiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false, length = 30)
    private String cuentaId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "monto", nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_transaccion", nullable = false, length = 20)
    private TipoTransaccion tipoTransaccion;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    /** true si el monto supera el umbral configurado de anomalía. */
    @Column(name = "es_anomalia")
    private boolean anomalia;

    public enum TipoTransaccion {
        DEPOSITO, RETIRO, TRANSFERENCIA, PAGO
    }
}
