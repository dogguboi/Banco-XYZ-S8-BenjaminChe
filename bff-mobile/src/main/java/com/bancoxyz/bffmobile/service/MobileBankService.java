package com.bancoxyz.bffmobile.service;

import com.bancoxyz.batch.model.CuentaInteres;
import com.bancoxyz.batch.model.MovimientoAnual;
import com.bancoxyz.batch.repository.CuentaInteresRepository;
import com.bancoxyz.batch.repository.MovimientoAnualRepository;
import com.bancoxyz.bffmobile.dto.CuentaMovilDto;
import com.bancoxyz.bffmobile.dto.ResumenMovilDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Lógica de presentación del canal Móvil: DTOs mínimos y volumen acotado.
 *
 * Exp3-S8: protegido con Circuit Breaker y Retry. En el canal móvil el
 * fallback es especialmente relevante, porque una app de banca que se
 * queda esperando una respuesta consume batería y datos del usuario; es
 * preferible una respuesta vacía inmediata que un timeout largo.
 */
@Service
public class MobileBankService {

    private static final Logger log = LoggerFactory.getLogger(MobileBankService.class);
    private static final String CB = "bancoDatos";

    /** Tamaño máximo de página para no saturar conexiones móviles. */
    public static final int MAX_PAGE_SIZE = 5;

    private final CuentaInteresRepository cuentaRepo;
    private final MovimientoAnualRepository movimientoRepo;

    public MobileBankService(CuentaInteresRepository cuentaRepo,
                             MovimientoAnualRepository movimientoRepo) {
        this.cuentaRepo = cuentaRepo;
        this.movimientoRepo = movimientoRepo;
    }

    @CircuitBreaker(name = CB, fallbackMethod = "listarCuentasFallback")
    @Retry(name = CB)
    public List<CuentaMovilDto> listarCuentas(int page, int size) {
        int tam = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ultimosCortes().stream()
                .skip((long) Math.max(page, 0) * tam)
                .limit(tam)
                .map(c -> new CuentaMovilDto(c.getCuentaId(),
                        c.getTipoCuenta().name().toLowerCase(), saldoActual(c)))
                .toList();
    }

    @CircuitBreaker(name = CB, fallbackMethod = "obtenerCuentaFallback")
    @Retry(name = CB)
    public Optional<CuentaMovilDto> obtenerCuenta(String cuentaId) {
        return cuentaRepo.findFirstByCuentaIdOrderByFechaDesc(cuentaId)
                .map(c -> new CuentaMovilDto(c.getCuentaId(),
                        c.getTipoCuenta().name().toLowerCase(), saldoActual(c)));
    }

    @CircuitBreaker(name = CB, fallbackMethod = "resumenFallback")
    @Retry(name = CB)
    public ResumenMovilDto resumen() {
        List<CuentaInteres> cortes = ultimosCortes();
        BigDecimal saldoTotal = cortes.stream()
                .map(this::saldoActual).reduce(BigDecimal.ZERO, BigDecimal::add);
        var ultimos = movimientoRepo
                .findAll(PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "fecha")))
                .map(this::aUltimoMovimiento)
                .getContent();
        return new ResumenMovilDto(saldoTotal, cortes.size(), ultimos);
    }

    /* ----------------------------- fallbacks -------------------------- */

    private List<CuentaMovilDto> listarCuentasFallback(int page, int size, Throwable t) {
        log.warn("[FALLBACK] listarCuentas: la base no respondió ({}).", t.toString());
        return List.of();
    }

    private Optional<CuentaMovilDto> obtenerCuentaFallback(String cuentaId, Throwable t) {
        log.warn("[FALLBACK] obtenerCuenta({}): la base no respondió ({}).", cuentaId, t.toString());
        return Optional.empty();
    }

    private ResumenMovilDto resumenFallback(Throwable t) {
        log.warn("[FALLBACK] resumen: la base no respondió ({}). Se devuelve resumen vacío.", t.toString());
        return new ResumenMovilDto(BigDecimal.ZERO, 0, List.of());
    }

    /* ------------------------------------------------------------------ */

    private ResumenMovilDto.UltimoMovimientoDto aUltimoMovimiento(MovimientoAnual m) {
        return new ResumenMovilDto.UltimoMovimientoDto(
                m.getFecha().toString(), m.getTipoOperacion(), m.getMonto());
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
