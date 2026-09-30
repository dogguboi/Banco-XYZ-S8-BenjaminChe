package com.bancoxyz.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de notificaciones y antifraude del Banco XYZ (Exp3-S8).
 *
 * Es el consumidor de la arquitectura orientada a eventos: escucha el
 * tópico banco.retiros y reacciona a cada retiro ejecutado en un cajero.
 * Podría caerse por completo sin que los cajeros dejen de operar: al
 * volver, recupera los eventos pendientes desde Kafka. Ese es el valor
 * del desacoplamiento asíncrono.
 */
@SpringBootApplication
public class NotificacionesApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificacionesApplication.class, args);
    }
}
