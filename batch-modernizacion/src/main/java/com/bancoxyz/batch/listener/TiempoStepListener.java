package com.bancoxyz.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

import java.time.Duration;

@Slf4j
public class TiempoStepListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("[{}] INICIO step (leídos/escritos se acumulan en el resumen final del Job)",
                stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long ms = -1;
        if (stepExecution.getStartTime() != null && stepExecution.getEndTime() != null) {
            ms = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
        }
        log.info("[{}] FIN step en {} ms | leídos={} escritos={} saltados={}",
                stepExecution.getStepName(), ms, stepExecution.getReadCount(),
                stepExecution.getWriteCount(), stepExecution.getSkipCount());
        return stepExecution.getExitStatus();
    }
}
