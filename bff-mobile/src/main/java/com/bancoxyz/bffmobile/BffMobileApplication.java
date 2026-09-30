package com.bancoxyz.bffmobile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * BFF del canal Móvil del Banco XYZ (Exp2-S5).
 * Respuestas ligeras: solo los datos esenciales, paginados y con
 * compresión HTTP, para reducir consumo de ancho de banda.
 */
@SpringBootApplication(scanBasePackages = "com.bancoxyz.bffmobile")
@EntityScan("com.bancoxyz.batch.model")
@EnableJpaRepositories("com.bancoxyz.batch.repository")
public class BffMobileApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffMobileApplication.class, args);
    }
}
