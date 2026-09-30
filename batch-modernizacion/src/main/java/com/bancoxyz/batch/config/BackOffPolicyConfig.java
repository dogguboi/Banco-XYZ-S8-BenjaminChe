package com.bancoxyz.batch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;

@Configuration
public class BackOffPolicyConfig {

    @Bean
    public BackOffPolicy retryBackOffPolicy(
            @Value("${app.batch.backoff.intervalo-inicial-ms:200}") long intervaloInicialMs,
            @Value("${app.batch.backoff.multiplicador:2.0}") double multiplicador,
            @Value("${app.batch.backoff.intervalo-maximo-ms:2000}") long intervaloMaximoMs) {

        ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(intervaloInicialMs);
        policy.setMultiplier(multiplicador);
        policy.setMaxInterval(intervaloMaximoMs);
        return policy;
    }
}
