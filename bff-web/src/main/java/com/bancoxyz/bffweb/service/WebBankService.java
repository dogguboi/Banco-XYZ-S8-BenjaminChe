package com.bancoxyz.bffweb.service;

import com.bancoxyz.batch.model.CuentaInteres;
import com.bancoxyz.batch.model.EstadoCuentaAnual;
import com.bancoxyz.batch.model.TransaccionDiaria;
import com.bancoxyz.batch.repository.*;
import com.bancoxyz.bffweb.dto.CuentaWebDto;
import com.bancoxyz.bffweb.dto.DashboardDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lógica de presentación del canal Web.
 *
 * Exp3-S8: las lecturas a la base pasan por Resilience4j. Si Oracle deja de
 * responder, el Retry reintenta y, si el fallo persiste, el Circuit Breaker
 * abre el circuito: las siguientes peticiones se resuelven de inmediato con
 * el método de fallback en lugar de dejar al usuario esperando un timeout.
 * El canal sigue respondiendo de forma degradada en vez de caerse.
 */
@Service
public class WebBankService {

    private static final Logger log = LoggerFactory.getLogger(WebBankService.class);

    /** Nombre de la instancia configurada en application.yml. */
    private static final String CB = "bancoDatos";

    private final CuentaInteresRepository cuentaRepo;
    private final TransaccionDiariaRepository transaccionRepo;
    private final MovimientoAnualRepository movimientoRepo;
    private final EstadoCuentaAnualRepository estadoRepo;

    public WebBankService(CuentaInteresRepository cuentaRepo,
                          TransaccionDiariaRepository transaccionRepo,
                          MovimientoAnualRepository movimientoRepo,
                          EstadoCuentaAnualRepository estadoRepo) {
        this.cuentaRepo = cuentaRepo;
        this.transaccionRepo = transaccionRepo;
        this.movimientoRepo = movimientoRepo;
        this.estadoRepo = estadoRepo;
    }

    /* ------------------------------------------------------------------ */
    /* Operaciones protegidas con Circuit Breaker + Retry                  */
    /* ------------------------------------------------------------------ */

    @CircuitBreaker(name = CB, fallbackMethod = "listarCuentasFallback")
    @Retry(name = CB)
    public List<CuentaWebDto> listarCuentas() {
        return cuentaRepo.findDistinctCuentaIdBy().stream()
                .map(this::componerCuenta)
                .flatMap(Optional::stream)
                .toList();
    }

    @CircuitBreaker(name = CB, fallbackMethod = "obtenerCuentaFallback")
    @Retry(name = CB)
    public Optional<CuentaWebDto> obtenerCuenta(String cuentaId) {
        return componerCuenta(cuentaId);
    }

    @CircuitBreaker(name = CB, fallbackMethod = "listarTransaccionesFallback")
    @Retry(name = CB)
    public List<TransaccionDiaria> listarTransacciones() {
        return transaccionRepo.findAll();
    }

    @CircuitBreaker(name = CB, fallbackMethod = "dashboardFallback")
    @Retry(name = CB)
    public DashboardDto dashboard() {
        List<CuentaInteres> cortes = ultimosCortes();
        BigDecimal saldoTotal = cortes.stream()
                .map(this::saldoActual).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interesTotal = cortes.stream()
                .map(c -> c.getInteresCalculado() != null ? c.getInteresCalculado() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Long> porTipo = cortes.stream()
                .collect(Collectors.groupingBy(c -> c.getTipoCuenta().name(), Collectors.counting()));

        return new DashboardDto(cortes.size(), saldoTotal, interesTotal, porTipo,
                transaccionRepo.count(), transaccionRepo.countByAnomaliaTrue(),
                movimientoRepo.count());
    }

    /* ------------------------------------------------------------------ */
    /* Métodos de fallback (respuesta degradada, nunca un error 500)       */
    /* ------------------------------------------------------------------ */

    private List<CuentaWebDto> listarCuentasFallback(Throwable t) {
        log.warn("[FALLBACK] listarCuentas: la base no respondió ({}). Se devuelve lista vacía.", t.toString());
        return List.of();
    }

    private Optional<CuentaWebDto> obtenerCuentaFallback(String cuentaId, Throwable t) {
        log.warn("[FALLBACK] obtenerCuenta({}): la base no respondió ({}).", cuentaId, t.toString());
        return Optional.empty();
    }

    private List<TransaccionDiaria> listarTransaccionesFallback(Throwable t) {
        log.warn("[FALLBACK] listarTransacciones: la base no respondió ({}).", t.toString());
        return List.of();
    }

    /**
     * Dashboard degradado: indicadores en cero en lugar de propagar el
     * error, para que la banca web pueda seguir pintando su interfaz.
     */
    private DashboardDto dashboardFallback(Throwable t) {
        log.warn("[FALLBACK] dashboard: la base no respondió ({}). Se devuelven indicadores en cero.", t.toString());
        return new DashboardDto(0, BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), 0, 0, 0);
    }

    /* ------------------------------------------------------------------ */

    private Optional<CuentaWebDto> componerCuenta(String cuentaId) {
        return cuentaRepo.findFirstByCuentaIdOrderByFechaDesc(cuentaId).map(c -> new CuentaWebDto(
                c.getCuentaId(), c.getTipoCuenta().name(), c.getEdadTitular(),
                c.getSaldoInicial(), c.getTasaInteres(), c.getInteresCalculado(),
                saldoActual(c), c.getFecha(),
                estadoRepo.findAll().stream()
                        .filter(e -> e.getCuentaId().equals(cuentaId))
                        .max(Comparator.comparing(EstadoCuentaAnual::getFechaGeneracion))
                        .orElse(null),
                movimientoRepo.findByCuentaId(cuentaId)));
    }

    private BigDecimal saldoActual(CuentaInteres c) {
        return c.getSaldoFinal() != null ? c.getSaldoFinal() : c.getSaldoInicial();
    }

    private List<CuentaInteres> ultimosCortes() {
        return cuentaRepo.findDistinctCuentaIdBy().stream()
                .map(cuentaRepo::findFirstByCuentaIdOrderByFechaDesc)
                .flatMap(Optional::stream)
                .toList();
    }
}
