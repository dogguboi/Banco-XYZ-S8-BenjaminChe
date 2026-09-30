package com.bancoxyz.batch.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class ParseUtils {

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    private ParseUtils() {
    }

    /** Intenta parsear la fecha probando varios formatos conocidos del legacy. */
    public static LocalDate parseFechaFlexible(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(valor.trim(), formato);
            } catch (DateTimeParseException ignored) {
                // probamos el siguiente formato
            }
        }
        return null;
    }

    /** Convierte a BigDecimal de forma segura; retorna null si no es numérico. */
    public static BigDecimal parseMonto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Convierte a Integer de forma segura; retorna null si no es numérico. */
    public static Integer parseEntero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
