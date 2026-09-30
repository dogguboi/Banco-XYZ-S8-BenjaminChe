package com.bancoxyz.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class JobLauncherRunner implements ApplicationRunner {

    private static final Map<String, String> ALIAS_A_JOB = Map.of(
            "transacciones", "reporteTransaccionesDiariasJob",
            "intereses", "calculoInteresesMensualesJob",
            "cuentas-anuales", "estadosCuentaAnualesJob"
    );

    private final JobLauncher jobLauncher;
    private final Job reporteTransaccionesDiariasJob;
    private final Job calculoInteresesMensualesJob;
    private final Job estadosCuentaAnualesJob;

    public JobLauncherRunner(JobLauncher jobLauncher,
                              @Qualifier("reporteTransaccionesDiariasJob") Job reporteTransaccionesDiariasJob,
                              @Qualifier("calculoInteresesMensualesJob") Job calculoInteresesMensualesJob,
                              @Qualifier("estadosCuentaAnualesJob") Job estadosCuentaAnualesJob) {
        this.jobLauncher = jobLauncher;
        this.reporteTransaccionesDiariasJob = reporteTransaccionesDiariasJob;
        this.calculoInteresesMensualesJob = calculoInteresesMensualesJob;
        this.estadosCuentaAnualesJob = estadosCuentaAnualesJob;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("job")) {
            log.info("No se especificó --job=... No se ejecutará ningún Job. " +
                    "Opciones válidas: transacciones | intereses | cuentas-anuales | all");
            return;
        }

        String solicitado = args.getOptionValues("job").get(0);

        if ("all".equalsIgnoreCase(solicitado)) {
            ejecutar(reporteTransaccionesDiariasJob);
            ejecutar(calculoInteresesMensualesJob);
            ejecutar(estadosCuentaAnualesJob);
            return;
        }

        String nombreJob = ALIAS_A_JOB.get(solicitado);
        if (nombreJob == null) {
            log.error("Job desconocido: '{}'. Opciones válidas: {}", solicitado, ALIAS_A_JOB.keySet());
            return;
        }

        Job job = switch (solicitado) {
            case "transacciones" -> reporteTransaccionesDiariasJob;
            case "intereses" -> calculoInteresesMensualesJob;
            case "cuentas-anuales" -> estadosCuentaAnualesJob;
            default -> null;
        };
        ejecutar(job);
    }

    private void ejecutar(Job job) throws Exception {
        var jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        var execution = jobLauncher.run(job, jobParameters);
        log.info(">>> Job '{}' finalizado con estado: {}", job.getName(), execution.getStatus());
    }
}
