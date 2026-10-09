package com.allstore.api.seguridad.config;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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
                                                   RespuestaErrorJson respuestaErrorJson) throws Exception {
        http
                // API sin cookies de sesión: el token va en cada petición, así que no aplica CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Público
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
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

                        // Clientes: lectura para todos, registro y edición en caja
                        .requestMatchers(HttpMethod.GET, "/api/clientes/**").authenticated()
                        .requestMatchers("/api/clientes/**").hasAnyRole(ADMIN, CAJERO)

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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
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
