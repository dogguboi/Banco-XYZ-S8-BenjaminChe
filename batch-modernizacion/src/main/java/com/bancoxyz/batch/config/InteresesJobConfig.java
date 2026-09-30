package com.bancoxyz.batch.config;

import com.bancoxyz.batch.listener.RegistroSkipListener;
import com.bancoxyz.batch.listener.ResumenJobListener;
import com.bancoxyz.batch.model.CuentaInteres;
import com.bancoxyz.batch.model.InteresRaw;
import com.bancoxyz.batch.policy.PoliticaReintentoPersonalizada;
import com.bancoxyz.batch.policy.PoliticaSaltoPersonalizada;
import com.bancoxyz.batch.processor.InteresItemProcessor;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
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
public class InteresesJobConfig {

    @Bean
    public FlatFileItemReader<InteresRaw> interesesReader(
            @Value("${app.archivos.intereses}") Resource resource) {
        return new FlatFileItemReaderBuilder<InteresRaw>()
                .name("interesesReader")
                .resource(resource)
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "tipoCuenta", "saldo", "tasaInteres", "edadTitular", "fecha")
                .targetType(InteresRaw.class)
                .build();
    }

    @Bean
    public JpaItemWriter<CuentaInteres> cuentaInteresWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<CuentaInteres>()
                .entityManagerFactory(emf)
                .build();
    }

    @Bean
    public Step calcularInteresesStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       FlatFileItemReader<InteresRaw> interesesReader,
                                       InteresItemProcessor interesItemProcessor,
                                       JpaItemWriter<CuentaInteres> cuentaInteresWriter,
                                       TaskExecutor batchTaskExecutor,
                                       BackOffPolicy retryBackOffPolicy,
                                       @Value("${app.batch.chunk-size:50}") int chunkSize,
                                       @Value("${app.batch.saltos-maximos:20}") int saltosMaximos,
                                       @Value("${app.batch.reintentos-maximos:3}") int reintentosMaximos) {
        return new StepBuilder("calcularInteresesStep", jobRepository)
                .<InteresRaw, CuentaInteres>chunk(chunkSize, transactionManager)
                .reader(interesesReader)
                .processor(interesItemProcessor)
                .writer(cuentaInteresWriter)
                .faultTolerant()
                .skipPolicy(new PoliticaSaltoPersonalizada(saltosMaximos))
                .retryPolicy(new PoliticaReintentoPersonalizada(reintentosMaximos))
                .backOffPolicy(retryBackOffPolicy)
                .listener(new RegistroSkipListener<InteresRaw, CuentaInteres>("intereses-mensuales"))
                .listener(new com.bancoxyz.batch.listener.TiempoStepListener())
                // --- Escalamiento elegido para este Job: STEP MULTI-THREADED ---
                .taskExecutor(batchTaskExecutor)
                .throttleLimit(4)
                .build();
    }

    @Bean
    public Job calculoInteresesMensualesJob(JobRepository jobRepository, Step calcularInteresesStep) {
        return new JobBuilder("calculoInteresesMensualesJob", jobRepository)
                .listener(new ResumenJobListener("Cálculo de Intereses Mensuales"))
                .start(calcularInteresesStep)
                .build();
    }
}
