package com.bancoxyz.batch.config;

import com.bancoxyz.batch.listener.RegistroSkipListener;
import com.bancoxyz.batch.listener.ResumenJobListener;
import com.bancoxyz.batch.model.CuentaAnualRaw;
import com.bancoxyz.batch.model.MovimientoAnual;
import com.bancoxyz.batch.partitioner.RangoLineasPartitioner;
import com.bancoxyz.batch.policy.PoliticaReintentoPersonalizada;
import com.bancoxyz.batch.policy.PoliticaSaltoPersonalizada;
import com.bancoxyz.batch.processor.CuentaAnualItemProcessor;
import com.bancoxyz.batch.repository.EstadoCuentaAnualRepository;
import com.bancoxyz.batch.repository.MovimientoAnualRepository;
import com.bancoxyz.batch.tasklet.CompilarEstadosCuentaTasklet;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
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
public class CuentasAnualesJobConfig {

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemReader<CuentaAnualRaw> cuentaAnualReader(
            @Value("${app.archivos.cuentas-anuales}") Resource resource,
            @Value("#{stepExecutionContext['startLine']}") Integer startLine,
            @Value("#{stepExecutionContext['maxItems']}") Integer maxItems) {

        FlatFileItemReader<CuentaAnualRaw> reader = new FlatFileItemReaderBuilder<CuentaAnualRaw>()
                .name("cuentaAnualReader")
                .resource(resource)
                .linesToSkip(startLine != null ? startLine : 1)
                .delimited()
                .names("cuentaId", "fecha", "tipoOperacion", "monto", "descripcion")
                .targetType(CuentaAnualRaw.class)
                .build();
        if (maxItems != null) {
            reader.setMaxItemCount(maxItems);
        }
        return reader;
    }

    @Bean
    public JpaItemWriter<MovimientoAnual> movimientoAnualWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<MovimientoAnual>()
                .entityManagerFactory(emf)
                .build();
    }

    @Bean
    public Partitioner cuentaAnualPartitioner(@Value("${app.archivos.cuentas-anuales}") Resource resource,
                                               @Value("${app.batch.particiones:4}") int particiones) {
        return new RangoLineasPartitioner(resource, particiones);
    }

    /**
     * Step WORKER: procesa una única partición (rango de líneas) del archivo.
     * Spring Batch crea una StepExecution independiente de este mismo Step
     * por cada partición generada por el {@link RangoLineasPartitioner}, y el
     * PartitionHandler las ejecuta en paralelo (ver particionMovimientosManagerStep).
     */
    @Bean
    public Step validarMovimientoAnualStep(JobRepository jobRepository,
                                            PlatformTransactionManager transactionManager,
                                            FlatFileItemReader<CuentaAnualRaw> cuentaAnualReader,
                                            CuentaAnualItemProcessor cuentaAnualItemProcessor,
                                            JpaItemWriter<MovimientoAnual> movimientoAnualWriter,
                                            BackOffPolicy retryBackOffPolicy,
                                            @Value("${app.batch.chunk-size:50}") int chunkSize,
                                            @Value("${app.batch.saltos-maximos:20}") int saltosMaximos,
                                            @Value("${app.batch.reintentos-maximos:3}") int reintentosMaximos) {
        return new StepBuilder("validarMovimientoAnualStep", jobRepository)
                .<CuentaAnualRaw, MovimientoAnual>chunk(chunkSize, transactionManager)
                .reader(cuentaAnualReader)
                .processor(cuentaAnualItemProcessor)
                .writer(movimientoAnualWriter)
                .faultTolerant()
                .skipPolicy(new PoliticaSaltoPersonalizada(saltosMaximos))
                .retryPolicy(new PoliticaReintentoPersonalizada(reintentosMaximos))
                .backOffPolicy(retryBackOffPolicy)
                .listener(new RegistroSkipListener<CuentaAnualRaw, MovimientoAnual>("cuentas-anuales"))
                .listener(new com.bancoxyz.batch.listener.TiempoStepListener())
                .build();
    }

    /**
     * Step MANAGER: reparte el trabajo entre particiones (vía el Partitioner)
     * y las ejecuta en paralelo delegando en el PartitionHandler, que asigna
     * cada partición al Step WORKER (validarMovimientoAnualStep). Al finalizar
     * todas las particiones, sus resultados quedan agregados (join/aggregate)
     * en este mismo Step antes de continuar el flujo del Job.
     */
    @Bean
    public Step particionMovimientosManagerStep(JobRepository jobRepository,
                                                 Step validarMovimientoAnualStep,
                                                 Partitioner cuentaAnualPartitioner,
                                                 TaskExecutor batchTaskExecutor,
                                                 @Value("${app.batch.particiones:4}") int particiones) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(validarMovimientoAnualStep);
        partitionHandler.setTaskExecutor(batchTaskExecutor);
        partitionHandler.setGridSize(particiones);

        return new StepBuilder("particionMovimientosManagerStep", jobRepository)
                .partitioner(validarMovimientoAnualStep.getName(), cuentaAnualPartitioner)
                .partitionHandler(partitionHandler)
                .build();
    }

    @Bean
    public Step compilarEstadosStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager,
                                     MovimientoAnualRepository movimientoAnualRepository,
                                     EstadoCuentaAnualRepository estadoCuentaAnualRepository,
                                     @Value("${app.reportes.directorio}") String directorioSalida) {
        return new StepBuilder("compilarEstadosStep", jobRepository)
                .tasklet(new CompilarEstadosCuentaTasklet(movimientoAnualRepository, estadoCuentaAnualRepository, directorioSalida),
                        transactionManager)
                .build();
    }

    @Bean
    public Job estadosCuentaAnualesJob(JobRepository jobRepository,
                                        Step particionMovimientosManagerStep,
                                        Step compilarEstadosStep) {
        return new JobBuilder("estadosCuentaAnualesJob", jobRepository)
                .listener(new ResumenJobListener("Generación de Estados de Cuenta Anuales"))
                .start(particionMovimientosManagerStep)
                .next(compilarEstadosStep)
                .build();
    }
}
