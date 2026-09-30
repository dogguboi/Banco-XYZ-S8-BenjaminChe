package com.bancoxyz.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servidor de autorización OAuth 2.0 del Banco XYZ (Exp3-S8).
 *
 * Centraliza la identidad de los tres canales: emite access tokens JWT
 * firmados con RSA y publica su clave pública en el JWK Set
 * (/oauth2/jwks), que los BFF usan para validar sin conocer secretos.
 *
 * Sustituye el esquema de la semana 5, donde cada BFF firmaba sus propios
 * tokens con un secreto compartido (HMAC).
 */
@SpringBootApplication
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
