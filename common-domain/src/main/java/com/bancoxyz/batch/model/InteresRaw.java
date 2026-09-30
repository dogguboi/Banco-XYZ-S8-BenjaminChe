package com.bancoxyz.batch.model;

import lombok.Data;

/** Fila cruda del archivo intereses.csv, campos en String (ver TransaccionRaw). */
@Data
public class InteresRaw {
    private String cuentaId;
    private String tipoCuenta;
    private String saldo;
    private String tasaInteres;
    private String edadTitular;
    private String fecha;
}
