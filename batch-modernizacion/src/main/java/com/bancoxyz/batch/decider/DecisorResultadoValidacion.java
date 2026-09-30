package com.bancoxyz.batch.decider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;

@Slf4j
public class DecisorResultadoValidacion implements JobExecutionDecider {

    public static final String CON_ANOMALIAS = "CON_ANOMALIAS";
    public static final String SIN_ANOMALIAS = "SIN_ANOMALIAS";

    private final String nombreStepAEvaluar;

    public DecisorResultadoValidacion(String nombreStepAEvaluar) {
        this.nombreStepAEvaluar = nombreStepAEvaluar;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        long totalSaltados = jobExecution.getStepExecutions().stream()
                .filter(se -> se.getStepName().equals(nombreStepAEvaluar))
                .mapToLong(StepExecution::getSkipCount)
                .sum();

        if (totalSaltados > 0) {
            log.warn("Decisor: el step '{}' saltó {} registro(s) inválido(s). " +
                    "Flujo -> resumen CON alerta.", nombreStepAEvaluar, totalSaltados);
            return new FlowExecutionStatus(CON_ANOMALIAS);
        }

        log.info("Decisor: el step '{}' no tuvo registros saltados. Flujo -> resumen normal.", nombreStepAEvaluar);
        return new FlowExecutionStatus(SIN_ANOMALIAS);
    }
}
