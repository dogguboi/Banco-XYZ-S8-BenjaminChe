package com.bancoxyz.batch.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento individual leído de cuentas_anuales.csv. Es la unidad base a
 * partir de la cual el Job de estados de cuenta anuales compila (agrega)
 * un {@link EstadoCuentaAnual} por cada cuenta, para auditoría.
 */
@Entity
@Table(name = "movimiento_anual")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false, length = 30)
    private String cuentaId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "tipo_operacion", nullable = false, length = 30)
    private String tipoOperacion;

    @Column(name = "monto", nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}
