package com.bancoxyz.batch.partitioner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class RangoLineasPartitioner implements Partitioner {

    private final Resource recurso;
    private final int numeroParticiones;

    public RangoLineasPartitioner(Resource recurso, int numeroParticiones) {
        this.recurso = recurso;
        this.numeroParticiones = numeroParticiones;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalLineasDatos = contarLineasDeDatos();
        int particiones = Math.max(1, numeroParticiones);
        int tamanoBase = (int) Math.ceil((double) totalLineasDatos / particiones);

        Map<String, ExecutionContext> resultado = new LinkedHashMap<>();
        int inicio = 0;
        int numeroParticion = 0;

        while (inicio < totalLineasDatos) {
            int cantidad = Math.min(tamanoBase, totalLineasDatos - inicio);

            ExecutionContext contexto = new ExecutionContext();
            // linesToSkip: 1 (encabezado) + líneas de datos ya asignadas a particiones previas
            contexto.putInt("startLine", inicio + 1);
            contexto.putInt("maxItems", cantidad);
            contexto.putString("nombreParticion", "particion-" + numeroParticion);

            resultado.put("particion" + numeroParticion, contexto);
            log.info("Partición {} -> startLine(skip)={}, cantidadItems={}",
                    numeroParticion, inicio + 1, cantidad);

            inicio += cantidad;
            numeroParticion++;
        }
        return resultado;
    }

    private int contarLineasDeDatos() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            long total = br.lines().count();
            // se resta 1 por el encabezado
            return (int) Math.max(0, total - 1);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo para particionar: " + recurso, e);
        }
    }
}
