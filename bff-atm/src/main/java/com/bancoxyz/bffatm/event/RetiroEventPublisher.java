package com.bancoxyz.bffatm.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RetiroEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RetiroEventPublisher.class);

    private final KafkaTemplate<String, RetiroEvent> kafkaTemplate;
    private final String topico;

    public RetiroEventPublisher(KafkaTemplate<String, RetiroEvent> kafkaTemplate,
                                @Value("${bff.atm.topico-retiros:banco.retiros}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.topico = topico;
    }

    public void publicar(RetiroEvent evento) {
        try {
            kafkaTemplate.send(topico, evento.cuentaId(), evento)
                .whenComplete((resultado, error) -> {
                    if (error != null) {
                        log.error("[KAFKA] No se pudo publicar el retiro {}: {}",
                                evento.comprobante(), error.getMessage());
                    } else {
                        log.info("[KAFKA] Evento publicado en {} particion={} offset={} comprobante={}",
                                topico,
                                resultado.getRecordMetadata().partition(),
                                resultado.getRecordMetadata().offset(),
                                evento.comprobante());
                    }
                });
        } catch (Exception e) {
            log.error("[KAFKA] Error inesperado publicando el retiro {}: {}",
                    evento.comprobante(), e.getMessage());
        }
    }
}
