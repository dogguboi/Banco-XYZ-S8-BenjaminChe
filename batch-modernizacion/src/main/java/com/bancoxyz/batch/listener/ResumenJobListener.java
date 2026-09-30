package com.bancoxyz.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;

@Slf4j
public class ResumenJobListener extends JobExecutionListenerSupport {

    private final String nombreProceso;

    public ResumenJobListener(String nombreProceso) {
        this.nombreProceso = nombreProceso;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("========================================================");
        log.info(" INICIANDO JOB: {} (jobExecutionId={})", nombreProceso, jobExecution.getId());
        log.info("========================================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("========================================================");
        log.info(" RESUMEN JOB: {} -> ESTADO FINAL: {}", nombreProceso, jobExecution.getStatus());
        for (StepExecution step : jobExecution.getStepExecutions()) {
            log.info(" Step [{}] leidos={} procesados/escritos={} saltados(read+process+write)={} " +
                            "commits={} rollbacks={} duracionMs={}",
                    step.getStepName(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getSkipCount(),
                    step.getCommitCount(),
                    step.getRollbackCount(),
                    (step.getEndTime() != null && step.getStartTime() != null)
                            ? java.time.Duration.between(step.getStartTime(), step.getEndTime()).toMillis()
                            : -1);
        }
        log.info("========================================================");
    }
}
