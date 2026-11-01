package com.allstore.api.monitoreo;

import com.allstore.api.seguridad.config.RespuestaErrorJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Protege /actuator/prometheus, que lee Prometheus cada pocos segundos: no puede iniciar sesión
 * con un token de 8 horas, así que usa autenticación básica con el usuario {@code metricas} y la
 * clave de METRICAS_TOKEN. Sin esa variable, las métricas no se pueden leer desde fuera.
 *
 * <p>Las métricas no tienen datos de clientes, pero sí del negocio (ventas por tipo, colas): no
 * deben quedar públicas.
 */
public class AccesoMetricas extends OncePerRequestFilter {

    public static final String RUTA = "/actuator/prometheus";
    static final String USUARIO = "metricas";

    private final byte[] esperado;
    private final RespuestaErrorJson respuestaErrorJson;

    public AccesoMetricas(String token, RespuestaErrorJson respuestaErrorJson) {
        this.esperado = StringUtils.hasText(token)
                ? ("Basic " + Base64.getEncoder().encodeToString((USUARIO + ":" + token).getBytes(StandardCharsets.UTF_8)))
                .getBytes(StandardCharsets.UTF_8)
                : null;
        this.respuestaErrorJson = respuestaErrorJson;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(RUTA);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (esperado == null) {
            respuestaErrorJson.escribir(request, response, HttpStatus.NOT_FOUND, "Recurso no encontrado");
            return;
        }
        String recibido = request.getHeader("Authorization");
        // Comparación en tiempo constante: no revela cuántos caracteres coinciden.
        if (recibido == null || !MessageDigest.isEqual(esperado, recibido.getBytes(StandardCharsets.UTF_8))) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"metricas\"");
            respuestaErrorJson.escribir(request, response, HttpStatus.UNAUTHORIZED, "Credenciales de métricas inválidas");
            return;
        }
        chain.doFilter(request, response);
    }
}
