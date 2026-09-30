package com.bancoxyz.batch.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Entidad de destino para el Job "Cálculo de Intereses Mensuales". */
@Entity
@Table(name = "cuenta_interes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CuentaInteres {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false, length = 30)
    private String cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cuenta", nullable = false, length = 20)
    private TipoCuenta tipoCuenta;

    @Column(name = "saldo_inicial", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoInicial;

    @Column(name = "tasa_interes", nullable = false, precision = 6, scale = 4)
    private BigDecimal tasaInteres;

    @Column(name = "edad_titular", nullable = false)
    private Integer edadTitular;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "interes_calculado", precision = 19, scale = 2)
    private BigDecimal interesCalculado;

    @Column(name = "saldo_final", precision = 19, scale = 2)
    private BigDecimal saldoFinal;

    public enum TipoCuenta {
        AHORRO, PRESTAMO
    }
}
