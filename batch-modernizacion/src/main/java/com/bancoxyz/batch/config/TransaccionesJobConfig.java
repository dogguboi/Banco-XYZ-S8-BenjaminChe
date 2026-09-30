package com.bancoxyz.batch.config;

import com.bancoxyz.batch.decider.DecisorResultadoValidacion;
import com.bancoxyz.batch.listener.RegistroSkipListener;
import com.bancoxyz.batch.listener.ResumenJobListener;
import com.bancoxyz.batch.model.TransaccionDiaria;
import com.bancoxyz.batch.model.TransaccionRaw;
import com.bancoxyz.batch.policy.PoliticaReintentoPersonalizada;
import com.bancoxyz.batch.policy.PoliticaSaltoPersonalizada;
import com.bancoxyz.batch.processor.TransaccionItemProcessor;
import com.bancoxyz.batch.repository.TransaccionDiariaRepository;
import com.bancoxyz.batch.tasklet.ResumenTransaccionesTasklet;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TransaccionesJobConfig {

    @Bean
    public FlatFileItemReader<TransaccionRaw> transaccionesReader(
            @Value("${app.archivos.transacciones}") Resource resource) {
        return new FlatFileItemReaderBuilder<TransaccionRaw>()
                .name("transaccionesReader")
                .resource(resource)
                .linesToSkip(1)
                .delimited()
                .names("id", "cuentaId", "fecha", "monto", "tipoTransaccion", "descripcion")
                .targetType(TransaccionRaw.class)
                .build();
    }

    @Bean
    public JpaItemWriter<TransaccionDiaria> transaccionDiariaWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<TransaccionDiaria>()
                .entityManagerFactory(emf)
                .build();
    }

    @Bean
    public Step validarTransaccionesStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          FlatFileItemReader<TransaccionRaw> transaccionesReader,
                                          TransaccionItemProcessor transaccionItemProcessor,
                                          JpaItemWriter<TransaccionDiaria> transaccionDiariaWriter,
                                          TaskExecutor batchTaskExecutor,
                                          BackOffPolicy retryBackOffPolicy,
                                          @Value("${app.batch.chunk-size:50}") int chunkSize,
                                          @Value("${app.batch.saltos-maximos:20}") int saltosMaximos,
                                          @Value("${app.batch.reintentos-maximos:3}") int reintentosMaximos) {
        return new StepBuilder("validarTransaccionesStep", jobRepository)
                .<TransaccionRaw, TransaccionDiaria>chunk(chunkSize, transactionManager)
                .reader(transaccionesReader)
                .processor(transaccionItemProcessor)
                .writer(transaccionDiariaWriter)
                .faultTolerant()
                .skipPolicy(new PoliticaSaltoPersonalizada(saltosMaximos))
                .retryPolicy(new PoliticaReintentoPersonalizada(reintentosMaximos))
                .backOffPolicy(retryBackOffPolicy)
                .listener(new RegistroSkipListener<TransaccionRaw, TransaccionDiaria>("transacciones-diarias"))
                .listener(new com.bancoxyz.batch.listener.TiempoStepListener())
                // --- Escalamiento elegido para este Job: STEP MULTI-THREADED ---
                .taskExecutor(batchTaskExecutor)
                .throttleLimit(4)
                .build();
    }

    @Bean
    public Step resumenTransaccionesStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          TransaccionDiariaRepository transaccionDiariaRepository,
                                          @Value("${app.reportes.directorio}") String directorioSalida) {
        return new StepBuilder("resumenTransaccionesStep", jobRepository)
                .tasklet(new ResumenTransaccionesTasklet(transaccionDiariaRepository, directorioSalida, false), transactionManager)
                .build();
    }

    /**
     * Mismo reporte de negocio que resumenTransaccionesStep, pero marcado
     * con el banner de alerta operativa. Es el destino del flujo cuando el
     * {@link DecisorResultadoValidacion} detecta que hubo registros saltados.
     */
    @Bean
    public Step resumenTransaccionesConAlertaStep(JobRepository jobRepository,
                                                   PlatformTransactionManager transactionManager,
                                                   TransaccionDiariaRepository transaccionDiariaRepository,
                                                   @Value("${app.reportes.directorio}") String directorioSalida) {
        return new StepBuilder("resumenTransaccionesConAlertaStep", jobRepository)
                .tasklet(new ResumenTransaccionesTasklet(transaccionDiariaRepository, directorioSalida, true), transactionManager)
                .build();
    }

    @Bean
    public JobExecutionDecider decisorResultadoValidacionTransacciones() {
        return new DecisorResultadoValidacion("validarTransaccionesStep");
    }

    @Bean
    public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
                                               Step validarTransaccionesStep,
                                               Step resumenTransaccionesStep,
                                               Step resumenTransaccionesConAlertaStep,
                                               JobExecutionDecider decisorResultadoValidacionTransacciones) {
        return new JobBuilder("reporteTransaccionesDiariasJob", jobRepository)
                .listener(new ResumenJobListener("Reporte de Transacciones Diarias"))
                .start(validarTransaccionesStep)
                .next(decisorResultadoValidacionTransacciones)
                    .on(DecisorResultadoValidacion.CON_ANOMALIAS).to(resumenTransaccionesConAlertaStep)
                .from(decisorResultadoValidacionTransacciones)
                    .on(DecisorResultadoValidacion.SIN_ANOMALIAS).to(resumenTransaccionesStep)
                .end()
                .build();
    }
}
