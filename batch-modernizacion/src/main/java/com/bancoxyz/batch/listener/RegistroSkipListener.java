package com.bancoxyz.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;

@Slf4j
public class RegistroSkipListener<T, S> implements SkipListener<T, S> {

    private final String nombreProceso;

    public RegistroSkipListener(String nombreProceso) {
        this.nombreProceso = nombreProceso;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[{}] SKIP en lectura: {}", nombreProceso, t.getMessage());
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        log.warn("[{}] SKIP en proceso, item='{}': {}", nombreProceso, item, t.getMessage());
    }

    @Override
    public void onSkipInWrite(S item, Throwable t) {
        log.warn("[{}] SKIP en escritura, item='{}': {}", nombreProceso, item, t.getMessage());
    }
}
