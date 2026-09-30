package com.bancoxyz.batch.policy;

import com.bancoxyz.batch.exception.ErrorTransitorioException;
import com.bancoxyz.batch.exception.RegistroInvalidoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;

@Slf4j
public class PoliticaSaltoPersonalizada implements SkipPolicy {

    private final int limiteSaltos;
    private int saltosContados = 0;

    public PoliticaSaltoPersonalizada(int limiteSaltos) {
        this.limiteSaltos = limiteSaltos;
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        boolean esSaltable = (t instanceof RegistroInvalidoException) || (t instanceof ErrorTransitorioException);

        if (!esSaltable) {
            log.error("Excepción NO contemplada para skip, se detiene el step: {}", t.toString());
            return false;
        }

        if (skipCount >= limiteSaltos) {
            log.error("Límite de saltos ({}) excedido. Deteniendo el step.", limiteSaltos);
            throw new SkipLimitExceededException(limiteSaltos, t);
        }

        saltosContados++;
        log.warn("Registro saltado (#{} de máx {}): {}", saltosContados, limiteSaltos, t.getMessage());
        return true;
    }
}
