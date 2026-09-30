package com.bancoxyz.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica de punta a punta que los 3 Jobs completan con estado COMPLETED
 * usando la base de datos en memoria (perfil de test), incluso con los
 * datos "sucios" del dataset de ejemplo (se espera que los registros
 * inválidos se salten y el Job igual finalice correctamente).
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class JobsIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("reporteTransaccionesDiariasJob")
    private Job reporteTransaccionesDiariasJob;

    @Autowired
    @Qualifier("calculoInteresesMensualesJob")
    private Job calculoInteresesMensualesJob;

    @Autowired
    @Qualifier("estadosCuentaAnualesJob")
    private Job estadosCuentaAnualesJob;

    @Test
    void elJobDeTransaccionesDiariasCompletaCorrectamente() throws Exception {
        var params = new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters();
        var execution = jobLauncher.run(reporteTransaccionesDiariasJob, params);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void elJobDeInteresesMensualesCompletaCorrectamente() throws Exception {
        var params = new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters();
        var execution = jobLauncher.run(calculoInteresesMensualesJob, params);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void elJobDeEstadosCuentaAnualesCompletaCorrectamente() throws Exception {
        var params = new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters();
        var execution = jobLauncher.run(estadosCuentaAnualesJob, params);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
