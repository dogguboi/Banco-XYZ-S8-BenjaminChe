package com.bancoxyz.batch.model;

import lombok.Data;

/** Fila cruda del archivo cuentas_anuales.csv, campos en String (ver TransaccionRaw). */
@Data
public class CuentaAnualRaw {
    private String cuentaId;
    private String fecha;
    private String tipoOperacion;
    private String monto;
    private String descripcion;
}
