package com.bancoxyz.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Configuración del servidor de autorización OAuth 2.0.
 *
 * Registra un cliente OAuth2 por cada canal del Banco XYZ. Cada uno usa el
 * flujo client_credentials (comunicación máquina a máquina: el frontend de
 * cada canal es un cliente confidencial del banco) y obtiene únicamente los
 * scopes de su canal, lo que mantiene el aislamiento entre canales logrado
 * en la semana 5, pero ahora con identidad centralizada y firma asimétrica.
 *
 * Tiempos de vida diferenciados por criticidad: 30 min web, 15 min móvil y
 * 5 min cajeros.
 */
@Configuration
public class AuthorizationServerConfig {

    /* ---------------------------------------------------------------- */
    /* Cadena de filtros del protocolo OAuth2 (endpoints /oauth2/**)     */
    /* ---------------------------------------------------------------- */

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        return http.build();
    }

    /** Resto de rutas (actuator, error) sin autenticación. */
    @Bean
    @Order(2)
    SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /* ---------------------------------------------------------------- */
    /* Clientes registrados: uno por canal                               */
    /* ---------------------------------------------------------------- */

    @Bean
    RegisteredClientRepository registeredClientRepository(PasswordEncoder encoder) {
        RegisteredClient web = canal("bff-web-client", "web-secret", "web.read", 30, encoder);
        RegisteredClient movil = canal("bff-mobile-client", "mobile-secret", "mobile.read", 15, encoder);
        RegisteredClient atm = canalAtm(encoder);
        return new InMemoryRegisteredClientRepository(web, movil, atm);
    }

    /** Cliente de un canal de solo lectura (web / móvil). */
    private RegisteredClient canal(String clientId, String secret, String scope,
                                   int minutos, PasswordEncoder encoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(encoder.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(scope)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(minutos))
                        .build())
                .build();
    }

    /**
     * Cliente del canal Cajeros: además de consultar, puede ejecutar
     * retiros, por lo que recibe un scope de escritura adicional y el
     * token de vida más corta del sistema (5 minutos).
     */
    private RegisteredClient canalAtm(PasswordEncoder encoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("bff-atm-client")
                .clientSecret(encoder.encode("atm-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("atm.read")
                .scope("atm.write")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build();
    }

    /* ---------------------------------------------------------------- */
    /* Claim de canal en el token (mantiene el aislamiento de la S5)     */
    /* ---------------------------------------------------------------- */

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            String clientId = context.getRegisteredClient().getClientId();
            String canal = switch (clientId) {
                case "bff-web-client" -> "WEB";
                case "bff-mobile-client" -> "MOBILE";
                case "bff-atm-client" -> "ATM";
                default -> "DESCONOCIDO";
            };
            JwtClaimsSet.Builder claims = context.getClaims();
            claims.claim("canal", canal);
            claims.claim("banco", "XYZ");
        };
    }

    /* ---------------------------------------------------------------- */
    /* Clave RSA para firmar los tokens + JWK Set público                */
    /* ---------------------------------------------------------------- */

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generarRsaKey();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private static RSAKey generarRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar la clave RSA del Authorization Server", e);
        }
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            @Value("${auth.issuer:http://localhost:9000}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Scopes disponibles, expuestos para documentación. */
    public static final List<String> SCOPES = List.of("web.read", "mobile.read", "atm.read", "atm.write");
}
