package com.bancoxyz.bffatm.service;

import com.bancoxyz.batch.model.CuentaInteres;
import com.bancoxyz.batch.model.MovimientoAnual;
import com.bancoxyz.batch.repository.CuentaInteresRepository;
import com.bancoxyz.batch.repository.MovimientoAnualRepository;
import com.bancoxyz.bffatm.dto.RetiroResponse;
import com.bancoxyz.bffatm.dto.SaldoDto;
import com.bancoxyz.bffatm.event.RetiroEvent;
import com.bancoxyz.bffatm.event.RetiroEventPublisher;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Operaciones críticas del canal Cajeros.
 *
 * Integridad (S5): el retiro se ejecuta en una transacción que actualiza el
 * saldo en cuenta_interes y registra el movimiento en movimiento_anual.
 *
 * Exp3-S8:
 *  - La consulta de saldo está protegida con Circuit Breaker + Retry y
 *    cuenta con un fallback que informa saldo no disponible.
 *  - El retiro NO lleva fallback: una operación de dinero jamás debe
 *    "degradarse"; si la base falla, la transacción se revierte y se
 *    informa el error. Aquí Resilience4j aporta el Retry (reintento ante
 *    fallos transitorios de red) sin inventar respuestas.
 *  - Tras confirmarse el retiro se publica un evento en Kafka, habilitando
 *    la arquitectura orientada a eventos.
 */
@Service
public class AtmBankService {

    private static final Logger audit = LoggerFactory.getLogger("ATM_AUDIT");
    private static final String CB = "bancoDatos";

    private final CuentaInteresRepository cuentaRepo;
    private final MovimientoAnualRepository movimientoRepo;
    private final RetiroEventPublisher eventPublisher;
    private final BigDecimal multiplo;
    private final BigDecimal topeRetiro;

    public AtmBankService(CuentaInteresRepository cuentaRepo,
                          MovimientoAnualRepository movimientoRepo,
                          RetiroEventPublisher eventPublisher,
                          @Value("${bff.atm.monto-multiplo:10}") BigDecimal multiplo,
                          @Value("${bff.atm.tope-retiro:2000}") BigDecimal topeRetiro) {
        this.cuentaRepo = cuentaRepo;
        this.movimientoRepo = movimientoRepo;
        this.eventPublisher = eventPublisher;
        this.multiplo = multiplo;
        this.topeRetiro = topeRetiro;
    }

    /* ------------------------------------------------------------------ */
    /* Consulta de saldo: resiliente y degradable                          */
    /* ------------------------------------------------------------------ */

    @CircuitBreaker(name = CB, fallbackMethod = "consultarSaldoFallback")
    @Retry(name = CB)
    public Optional<SaldoDto> consultarSaldo(String cuentaId, String operador) {
        Optional<SaldoDto> saldo = cuentaRepo.findFirstByCuentaIdOrderByFechaDesc(cuentaId)
                .map(c -> SaldoDto.clp(cuentaId, saldoActual(c)));
        audit.info("CONSULTA_SALDO cuenta={} operador={} resultado={}",
                cuentaId, operador, saldo.isPresent() ? "OK" : "CUENTA_NO_EXISTE");
        return saldo;
    }

    private Optional<SaldoDto> consultarSaldoFallback(String cuentaId, String operador, Throwable t) {
        audit.warn("[FALLBACK] CONSULTA_SALDO cuenta={} operador={} motivo={}",
                cuentaId, operador, t.toString());
        return Optional.empty();
    }

    /* ------------------------------------------------------------------ */
    /* Retiro: transaccional, con reintento y publicación de evento        */
    /* ------------------------------------------------------------------ */

    @Transactional
    @Retry(name = "retiroCritico")
    public RetiroResponse retirar(String cuentaId, BigDecimal monto, String operador) {
        CuentaInteres cuenta = cuentaRepo.findFirstByCuentaIdOrderByFechaDesc(cuentaId)
                .orElseThrow(() -> rechazo(cuentaId, monto, operador, "CUENTA_NO_EXISTE"));

        if (monto == null || monto.signum() <= 0)
            throw rechazo(cuentaId, monto, operador, "MONTO_INVALIDO");
        if (monto.remainder(multiplo).signum() != 0)
            throw rechazo(cuentaId, monto, operador, "MONTO_NO_MULTIPLO_DE_" + multiplo);
        if (monto.compareTo(topeRetiro) > 0)
            throw rechazo(cuentaId, monto, operador, "SUPERA_TOPE_" + topeRetiro);

        BigDecimal saldo = saldoActual(cuenta);
        if (saldo.compareTo(monto) < 0)
            throw rechazo(cuentaId, monto, operador, "SALDO_INSUFICIENTE");

        // 1) Actualizar el saldo de la cuenta
        BigDecimal restante = saldo.subtract(monto);
        cuenta.setSaldoFinal(restante);
        cuentaRepo.save(cuenta);

        // 2) Registrar el movimiento como rastro de auditoría persistente
        String comprobante = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        movimientoRepo.save(new MovimientoAnual(null, cuentaId, LocalDate.now(),
                "retiro_atm", monto.negate(),
                "Retiro en cajero " + operador + " comprobante " + comprobante));

        audit.info("RETIRO_OK cuenta={} monto={} restante={} operador={} comprobante={}",
                cuentaId, monto, restante, operador, comprobante);

        // 3) Publicar el evento de dominio (asíncrono, no bloquea al cajero)
        eventPublisher.publicar(RetiroEvent.de(comprobante, cuentaId, monto, restante, operador));

        return new RetiroResponse(comprobante, cuentaId, monto, restante, Instant.now());
    }

    /* ------------------------------------------------------------------ */

    private BigDecimal saldoActual(CuentaInteres c) {
        return c.getSaldoFinal() != null ? c.getSaldoFinal() : c.getSaldoInicial();
    }

    private OperacionRechazadaException rechazo(String cuentaId, BigDecimal monto,
                                                String operador, String motivo) {
        audit.warn("RETIRO_RECHAZADO cuenta={} monto={} operador={} motivo={}",
                cuentaId, monto, operador, motivo);
        return new OperacionRechazadaException(motivo);
    }

    /**
     * Excepción de negocio del canal ATM (revierte la transacción).
     * Está excluida del Retry: un rechazo por reglas de negocio no debe
     * reintentarse, solo los fallos técnicos transitorios.
     */
    public static class OperacionRechazadaException extends RuntimeException {
        public OperacionRechazadaException(String motivo) {
            super(motivo);
        }
    }
}
