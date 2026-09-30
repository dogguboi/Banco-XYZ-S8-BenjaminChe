package com.bancoxyz.batch.exception;

public class ErrorTransitorioException extends RuntimeException {

    public ErrorTransitorioException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public ErrorTransitorioException(String mensaje) {
        super(mensaje);
    }
}
