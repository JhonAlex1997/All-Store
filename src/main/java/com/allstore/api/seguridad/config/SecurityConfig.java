package com.allstore.api.seguridad.config;

import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import com.allstore.api.monitoreo.AccesoMetricas;
import com.allstore.api.monitoreo.UsuarioEnRegistro;
import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.repository.UsuarioRepository;
import com.allstore.api.seguridad.service.JwtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Seguridad de la API: sesiones sin estado con JWT y la matriz de permisos completa.
 *
 * <p>Regla general: <b>todo lo que no está explícitamente permitido abajo se niega</b>. Al
 * agregar un módulo nuevo hay que darle aquí sus permisos; si se olvida, sus endpoints quedan
 * cerrados (falla segura), no abiertos.
 */
@Configuration
@EnableConfigurationProperties(SeguridadProperties.class)
public class SecurityConfig {

    private static final String ADMIN = Rol.ADMIN.name();
    private static final String CAJERO = Rol.CAJERO.name();
    private static final String ALMACENERO = Rol.ALMACENERO.name();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioRepository usuarioRepository,
                                                   RespuestaErrorJson respuestaErrorJson,
                                                   SeguridadProperties properties) throws Exception {
        http
                // API sin cookies de sesión: el token va en cada petición, así que no aplica CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Cabeceras: además de las de Spring (nosniff, X-Frame-Options DENY, sin caché),
                // nunca enviar el Referer (los enlaces públicos llevan un código secreto en la URL) y,
                // en las respuestas JSON de la API, una CSP que no permite cargar ni incrustar nada.
                // Los PDF públicos y Swagger (solo dev) quedan fuera de la CSP para que se vean.
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new AndRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                                        new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/api/publico/**"))),
                                new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))))
                .authorizeHttpRequests(auth -> auth
                        // Público
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Revisión de salud (para el balanceador o un monitor de caídas): solo "UP"/"DOWN";
                        // el detalle (base, disco) lo ve el administrador con su sesión.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Prometheus: lo protege AccesoMetricas con METRICAS_TOKEN (no usa sesión).
                        .requestMatchers(HttpMethod.GET, AccesoMetricas.RUTA).permitAll()
                        // El resto de actuator (métricas, info): solo administrador
                        .requestMatchers("/actuator/**").hasRole(ADMIN)
                        // Enlace de descarga que recibe el cliente: solo lectura, protegido por un
                        // código aleatorio imposible de adivinar
                        .requestMatchers(HttpMethod.GET, "/api/publico/comprobantes/**").permitAll()
                        // Swagger solo existe con el perfil dev (en producción está apagado).
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        // Sesión propia: cualquier usuario autenticado
                        .requestMatchers("/api/auth/**").authenticated()

                        // Usuarios: solo administrador
                        .requestMatchers("/api/usuarios/**").hasRole(ADMIN)

                        // Configuración del emisor: lectura para todos, cambios solo administrador
                        .requestMatchers(HttpMethod.GET, "/api/empresa/**", "/api/establecimientos/**", "/api/series/**")
                        .authenticated()
                        .requestMatchers("/api/empresa/**", "/api/establecimientos/**", "/api/series/**").hasRole(ADMIN)

                        // Catálogo: lectura para todos (la caja necesita buscar productos), cambios
                        // para administrador y almacenero
                        .requestMatchers(HttpMethod.GET, "/api/productos/**", "/api/categorias/**", "/api/tallas/**",
                                "/api/colores/**").authenticated()
                        .requestMatchers("/api/productos/**", "/api/categorias/**", "/api/tallas/**", "/api/colores/**")
                        .hasAnyRole(ADMIN, ALMACENERO)

                        // Proveedores, compras y producción: administrador y almacenero
                        .requestMatchers("/api/proveedores/**", "/api/compras/**", "/api/produccion/**").hasAnyRole(ADMIN, ALMACENERO)

                        // Insumos de producción (con su costo): administrador y almacenero
                        .requestMatchers("/api/insumos/**").hasAnyRole(ADMIN, ALMACENERO)

                        // Campañas de promoción: la caja consulta precios; crear y cambiar, solo administrador
                        .requestMatchers(HttpMethod.GET, "/api/campanas/**").authenticated()
                        .requestMatchers("/api/campanas/**").hasRole(ADMIN)

                        // Clientes (datos personales: DNI, teléfono, correo): solo caja y administrador.
                        // El almacenero no los necesita: la guía de una venta toma al cliente del comprobante.
                        .requestMatchers("/api/clientes/**").hasAnyRole(ADMIN, CAJERO)

                        // Inventario: consulta para todos (la caja ve si hay stock), movimientos y
                        // traslados para administrador y almacenero
                        .requestMatchers(HttpMethod.GET, "/api/inventario/**").authenticated()
                        .requestMatchers("/api/inventario/**").hasAnyRole(ADMIN, ALMACENERO)

                        // Ventas y notas de crédito: caja y administrador; anular solo administrador
                        .requestMatchers(HttpMethod.POST, "/api/ventas/*/anular").hasRole(ADMIN)
                        // Los totales son los ingresos del negocio; el cajero lista solo sus ventas (ConsultaVentasService)
                        .requestMatchers(HttpMethod.GET, "/api/ventas/totales").hasRole(ADMIN)
                        .requestMatchers("/api/ventas/**").hasAnyRole(ADMIN, CAJERO)

                        // Caja: cada cajero abre, mueve y cierra la suya; el historial solo administrador
                        .requestMatchers(HttpMethod.GET, "/api/caja/sesiones/**").hasRole(ADMIN)
                        .requestMatchers("/api/caja/**").hasAnyRole(ADMIN, CAJERO)

                        // Guías de remisión: las emite quien despacha (caja en ventas con entrega,
                        // almacén en traslados) y el administrador
                        .requestMatchers("/api/guias/**").hasAnyRole(ADMIN, CAJERO, ALMACENERO)

                        // Contabilidad (registro de ventas para el SIRE): solo administrador
                        .requestMatchers("/api/contabilidad/**").hasRole(ADMIN)

                        // Facturación electrónica (envíos, XML, CDR): solo administrador
                        .requestMatchers("/api/sunat/**").hasRole(ADMIN)

                        // Alertas (comprobantes rechazados, plazos de SUNAT, certificado): solo administrador
                        .requestMatchers("/api/alertas/**").hasRole(ADMIN)

                        // Monitoreo (estado del sistema, errores recientes): solo administrador
                        .requestMatchers("/api/sistema/**").hasRole(ADMIN)

                        // Bitácora de acciones sensibles (quién anuló, cambió precios, desbloqueó...): solo administrador
                        .requestMatchers("/api/bitacora/**").hasRole(ADMIN)

                        // Reportes y exportaciones (ventas por producto, vendedor, hora; detalle para análisis): solo administrador
                        .requestMatchers("/api/reportes/**").hasRole(ADMIN)

                        // Todo lo demás, cerrado
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(noAutenticado(respuestaErrorJson)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(noAutenticado(respuestaErrorJson))
                        .accessDeniedHandler((request, response, ex) -> respuestaErrorJson.escribir(
                                request, response, HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción")))
                .addFilterAfter(new UsuarioVigenteFilter(usuarioRepository, respuestaErrorJson),
                        BearerTokenAuthenticationFilter.class)
                // El usuario en cada línea del log de la solicitud (después de validar el token)
                .addFilterAfter(new UsuarioEnRegistro(), UsuarioVigenteFilter.class)
                .addFilterBefore(new AccesoMetricas(properties.metricasToken(), respuestaErrorJson),
                        BearerTokenAuthenticationFilter.class)
                // Primero de todo: cuerpo de tamaño razonable y límite de intentos fallidos por IP.
                .addFilterBefore(new LimiteCuerpoSolicitud(properties.maximoCuerpoKb() * 1024L, respuestaErrorJson),
                        DisableEncodeUrlFilter.class)
                .addFilterBefore(new LimiteIntentosLogin(properties.intentosLoginPorIp(),
                                Duration.ofMinutes(properties.ventanaLoginMinutos()), Clock.systemUTC(), respuestaErrorJson),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt, con prefijo {bcrypt} para poder migrar de algoritmo en el futuro sin romper hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(SeguridadProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(SeguridadProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Además de la firma: emisor obligatorio y vencimiento sin tolerancia extra.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(JwtService.ISSUER),
                new JwtTimestampValidator(Duration.ZERO)));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SeguridadProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.corsOrigenes());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Idempotency-Key: la caja la envía al emitir comprobantes para que un reintento no duplique la venta.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", SolicitudIdempotente.CABECERA));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Sin token, o con un token inválido o vencido. */
    private static AuthenticationEntryPoint noAutenticado(RespuestaErrorJson respuestaErrorJson) {
        return (request, response, ex) -> respuestaErrorJson.escribir(request, response, HttpStatus.UNAUTHORIZED,
                "Debes iniciar sesión");
    }

    /** El claim "rol" del token se convierte en la autoridad ROLE_<rol> que usa hasRole(...). */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(JwtService.CLAIM_ROL);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    private static SecretKey clave(SeguridadProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
