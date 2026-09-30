package com.bancoxyz.batch.policy;

import com.bancoxyz.batch.exception.ErrorTransitorioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

@Slf4j
public class PoliticaReintentoPersonalizada implements RetryPolicy {

    private final int maxIntentos;

    public PoliticaReintentoPersonalizada(int maxIntentos) {
        this.maxIntentos = maxIntentos;
    }

    @Override
    public boolean canRetry(RetryContext context) {
        Throwable ultimoError = context.getLastThrowable();
        if (ultimoError == null) {
            return true; // primer intento, aún no ha fallado
        }
        boolean esTransitorio = ultimoError instanceof ErrorTransitorioException;
        boolean dentroDelLimite = context.getRetryCount() < maxIntentos;
        return esTransitorio && dentroDelLimite;
    }

    @Override
    public RetryContext open(RetryContext parent) {
        return new RetryContextSupport(parent);
    }

    @Override
    public void close(RetryContext context) {
        // no-op: no hay recursos que liberar
    }

    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
        ((RetryContextSupport) context).registerThrowable(throwable);
        if (throwable instanceof ErrorTransitorioException) {
            log.warn("Reintento {}/{} tras error transitorio: {}",
                    context.getRetryCount(), maxIntentos, throwable.getMessage());
        }
    }
}
