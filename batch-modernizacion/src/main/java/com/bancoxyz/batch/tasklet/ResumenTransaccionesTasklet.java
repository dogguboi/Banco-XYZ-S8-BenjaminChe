package com.bancoxyz.batch.tasklet;

import com.bancoxyz.batch.model.TransaccionDiaria;
import com.bancoxyz.batch.repository.TransaccionDiariaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Segundo Step del Job "Reporte de Transacciones Diarias": una vez que el
 * primer step validó/escribió todas las transacciones, este Tasklet compila
 * el resumen del día (totales por tipo, cantidad de anomalías) y lo escribe
 * en un archivo de texto bajo ./output, sirviendo como evidencia de negocio
 * del resultado del Job.
 */
@Slf4j
public class ResumenTransaccionesTasklet implements Tasklet {

    private final TransaccionDiariaRepository repository;
    private final String directorioSalida;
    /**
     * Cuando es true (camino "CON_ANOMALIAS" del {@link com.bancoxyz.batch.decider.DecisorResultadoValidacion}),
     * el reporte incluye un banner de alerta operativa.
     */
    private final boolean conAlerta;

    public ResumenTransaccionesTasklet(TransaccionDiariaRepository repository, String directorioSalida) {
        this(repository, directorioSalida, false);
    }

    public ResumenTransaccionesTasklet(TransaccionDiariaRepository repository, String directorioSalida, boolean conAlerta) {
        this.repository = repository;
        this.directorioSalida = directorioSalida;
        this.conAlerta = conAlerta;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<TransaccionDiaria> todas = repository.findAll();

        Map<TransaccionDiaria.TipoTransaccion, BigDecimal> totalesPorTipo = new EnumMap<>(TransaccionDiaria.TipoTransaccion.class);
        long totalAnomalias = 0;
        BigDecimal totalGeneral = BigDecimal.ZERO;

        for (TransaccionDiaria t : todas) {
            totalesPorTipo.merge(t.getTipoTransaccion(), t.getMonto(), BigDecimal::add);
            totalGeneral = totalGeneral.add(t.getMonto());
            if (t.isAnomalia()) {
                totalAnomalias++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("RESUMEN DIARIO DE TRANSACCIONES - Banco XYZ\n");
        if (conAlerta) {
            sb.append("*** ALERTA: se detectaron y descartaron registros inválidos en el archivo de origen. ");
            sb.append("Se recomienda que el equipo de Datos/Operaciones revise el archivo fuente. ***\n");
        }
        sb.append("Generado: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("--------------------------------------------------\n");
        sb.append("Total transacciones válidas procesadas: ").append(todas.size()).append("\n");
        sb.append("Total transacciones marcadas como ANOMALÍA: ").append(totalAnomalias).append("\n");
        sb.append("Monto total procesado: ").append(totalGeneral).append("\n\n");
        sb.append("Totales por tipo de transacción:\n");
        totalesPorTipo.forEach((tipo, monto) -> sb.append("  - ").append(tipo).append(": ").append(monto).append("\n"));

        Files.createDirectories(Path.of(directorioSalida));
        File archivo = new File(directorioSalida, "resumen_transacciones_diarias.txt");
        // Se fuerza UTF-8 explícitamente: el charset "por defecto" del sistema
        // operativo (especialmente en Windows) puede no coincidir con UTF-8,
        // corrompiendo tildes y "ñ" tanto en el archivo como en la consola.
        try (Writer fw = new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            log.error("No se pudo escribir el resumen de transacciones", e);
            throw e;
        }

        log.info("Reporte de transacciones diarias generado en: {}", archivo.getAbsolutePath());
        log.info(sb.toString());

        return RepeatStatus.FINISHED;
    }
}
