package com.bancoxyz.batch.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Estado de cuenta anual compilado (agregado) por cuenta. Generado en el
 * paso de agregación del Job "Generación de Estados de Cuenta Anuales",
 * a partir de todos los {@link MovimientoAnual} de esa cuenta.
 */
@Entity
@Table(name = "estado_cuenta_anual")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoCuentaAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false, length = 30)
    private String cuentaId;

    @Column(name = "total_depositos", precision = 19, scale = 2)
    private BigDecimal totalDepositos;

    @Column(name = "total_retiros", precision = 19, scale = 2)
    private BigDecimal totalRetiros;

    @Column(name = "total_otros", precision = 19, scale = 2)
    private BigDecimal totalOtros;

    @Column(name = "saldo_neto_anual", precision = 19, scale = 2)
    private BigDecimal saldoNetoAnual;

    @Column(name = "cantidad_movimientos")
    private Integer cantidadMovimientos;

    @Column(name = "fecha_generacion")
    private LocalDateTime fechaGeneracion;
}
