package com.bancoxyz.bffatm.dto;

import java.math.BigDecimal;

/** Solicitud de retiro desde un cajero automático. */
public record RetiroRequest(BigDecimal monto) {}
