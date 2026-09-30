package com.bancoxyz.notificaciones.consumer;

import com.bancoxyz.notificaciones.model.RetiroEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumidor del tópico banco.retiros.
 *
 * Por cada retiro publicado por el BFF de cajeros ejecuta dos reacciones
 * de negocio, ambas impensables de forma síncrona dentro del cajero:
 *   1. Notificación al cliente (simulada en el log).
 *   2. Evaluación antifraude: marca los retiros que igualan o superan el
 *      tope de alerta configurado.
 *
 * Conserva además las últimas notificaciones en memoria para poder
 * consultarlas por API y evidenciar el procesamiento.
 */
@Component
public class RetiroEventConsumer {

    private static final Logger log = LoggerFactory.getLogger("NOTIFICACIONES");

    private final BigDecimal montoAlerta;
    private final AtomicLong procesados = new AtomicLong();
    private final Deque<String> ultimas = new ConcurrentLinkedDeque<>();

    public RetiroEventConsumer(@Value("${notificaciones.monto-alerta:1000}") BigDecimal montoAlerta) {
        this.montoAlerta = montoAlerta;
    }

    @KafkaListener(
            topics = "${notificaciones.topico:banco.retiros}",
            groupId = "${spring.kafka.consumer.group-id:notificaciones}")
    public void onRetiro(@Payload RetiroEvent evento,
                         @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer particion,
                         @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {

        long n = procesados.incrementAndGet();
        log.info("EVENTO RECIBIDO #{} particion={} offset={} comprobante={} cuenta={} monto={}",
                n, particion, offset, evento.comprobante(), evento.cuentaId(), evento.monto());

        // 1) Notificación al cliente
        String aviso = String.format(
                "Banco XYZ: se realizó un retiro de $%s en el cajero %s. Saldo disponible: $%s. Comprobante %s.",
                evento.monto(), evento.operador(), evento.saldoRestante(), evento.comprobante());
        log.info("NOTIFICACION ENVIADA -> cuenta {}: {}", evento.cuentaId(), aviso);

        // 2) Evaluación antifraude
        if (evento.monto().compareTo(montoAlerta) >= 0) {
            log.warn("ALERTA ANTIFRAUDE: retiro de monto elevado (${} >= ${}) en la cuenta {}. " +
                     "Comprobante {} derivado a revisión manual.",
                    evento.monto(), montoAlerta, evento.cuentaId(), evento.comprobante());
        }

        registrar(aviso);
    }

    private void registrar(String aviso) {
        ultimas.addFirst(aviso);
        while (ultimas.size() > 20) ultimas.removeLast();
    }

    public long getProcesados() {
        return procesados.get();
    }

    public List<String> getUltimas() {
        return List.copyOf(ultimas);
    }
}
