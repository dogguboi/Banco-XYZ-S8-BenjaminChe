package com.bancoxyz.batch.tasklet;

import com.bancoxyz.batch.model.EstadoCuentaAnual;
import com.bancoxyz.batch.model.MovimientoAnual;
import com.bancoxyz.batch.repository.EstadoCuentaAnualRepository;
import com.bancoxyz.batch.repository.MovimientoAnualRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Segundo Step (no particionado) del Job "Generación de Estados de Cuenta
 * Anuales": una vez que el step particionado validó y persistió todos los
 * {@link MovimientoAnual}, este Tasklet agrupa por cuenta y compila el
 * {@link EstadoCuentaAnual}, además de generar un informe de auditoría en
 * texto plano.
 */
@Slf4j
@RequiredArgsConstructor
public class CompilarEstadosCuentaTasklet implements Tasklet {

    private final MovimientoAnualRepository movimientoRepository;
    private final EstadoCuentaAnualRepository estadoRepository;
    private final String directorioSalida;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<MovimientoAnual> movimientos = movimientoRepository.findAll();

        Map<String, List<MovimientoAnual>> porCuenta = movimientos.stream()
                .collect(Collectors.groupingBy(MovimientoAnual::getCuentaId));

        StringBuilder informe = new StringBuilder();
        informe.append("ESTADOS DE CUENTA ANUALES - Banco XYZ (informe de auditoría)\n");
        informe.append("Generado: ").append(LocalDateTime.now()).append("\n");
        informe.append("======================================================\n");

        for (Map.Entry<String, List<MovimientoAnual>> entry : porCuenta.entrySet()) {
            String cuentaId = entry.getKey();
            List<MovimientoAnual> movs = entry.getValue();

            BigDecimal depositos = sumaPorTipo(movs, "DEPOSITO");
            BigDecimal retiros = sumaPorTipo(movs, "RETIRO").abs();
            BigDecimal otros = movs.stream()
                    .filter(m -> !m.getTipoOperacion().equals("DEPOSITO") && !m.getTipoOperacion().equals("RETIRO"))
                    .map(MovimientoAnual::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saldoNeto = movs.stream().map(MovimientoAnual::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

            EstadoCuentaAnual estado = new EstadoCuentaAnual(null, cuentaId, depositos, retiros, otros,
                    saldoNeto, movs.size(), LocalDateTime.now());
            estadoRepository.save(estado);

            informe.append("Cuenta: ").append(cuentaId).append("\n");
            informe.append("  Movimientos: ").append(movs.size()).append("\n");
            informe.append("  Total depósitos: ").append(depositos).append("\n");
            informe.append("  Total retiros:   ").append(retiros).append("\n");
            informe.append("  Otros movimientos: ").append(otros).append("\n");
            informe.append("  Saldo neto anual: ").append(saldoNeto).append("\n");
            informe.append("------------------------------------------------------\n");
        }

        Files.createDirectories(Path.of(directorioSalida));
        File archivo = new File(directorioSalida, "estados_cuenta_anuales.txt");
        try (Writer fw = new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8)) {
            fw.write(informe.toString());
        } catch (IOException e) {
            log.error("No se pudo escribir el informe de estados de cuenta anuales", e);
            throw e;
        }

        log.info("Informe de auditoría de estados de cuenta anuales generado en: {}", archivo.getAbsolutePath());
        return RepeatStatus.FINISHED;
    }

    private BigDecimal sumaPorTipo(List<MovimientoAnual> movs, String tipo) {
        return movs.stream()
                .filter(m -> m.getTipoOperacion().equals(tipo))
                .map(MovimientoAnual::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
