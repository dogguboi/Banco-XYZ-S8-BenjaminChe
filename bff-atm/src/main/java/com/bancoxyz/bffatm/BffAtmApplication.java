package com.bancoxyz.bffatm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.bancoxyz.bffatm")
@EntityScan("com.bancoxyz.batch.model")
@EnableJpaRepositories("com.bancoxyz.batch.repository")
public class BffAtmApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffAtmApplication.class, args);
    }
}
