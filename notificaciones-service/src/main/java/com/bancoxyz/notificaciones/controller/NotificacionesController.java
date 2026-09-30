package com.bancoxyz.notificaciones.controller;

import com.bancoxyz.notificaciones.consumer.RetiroEventConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API de consulta del microservicio de notificaciones. Permite evidenciar,
 * sin leer los logs, cuántos eventos se han consumido desde Kafka y qué
 * notificaciones se generaron.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionesController {

    private final RetiroEventConsumer consumer;

    public NotificacionesController(RetiroEventConsumer consumer) {
        this.consumer = consumer;
    }

    @GetMapping
    public Map<String, Object> estado() {
        return Map.of(
                "servicio", "notificaciones-service",
                "eventosProcesados", consumer.getProcesados(),
                "ultimasNotificaciones", consumer.getUltimas());
    }

    @GetMapping("/ultimas")
    public List<String> ultimas() {
        return consumer.getUltimas();
    }
}
