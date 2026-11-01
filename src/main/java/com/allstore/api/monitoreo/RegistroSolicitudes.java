package com.allstore.api.monitoreo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cada solicitud recibe un código (cabecera {@code X-Request-Id}, que también lleva cada error de la
 * API) y deja una línea en el log: método, ruta, estado, duración, usuario e IP. Con el código que
 * reporta la caja se encuentra en el log todo lo que pasó en esa solicitud.
 *
 * <p>No registra el cuerpo ni la consulta (?busqueda=...): pueden llevar DNI, nombres o contraseñas.
 * Un balanceador o Nginx puede enviar su propio X-Request-Id; se acepta si es un código simple.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistroSolicitudes extends OncePerRequestFilter {

    public static final String CABECERA = "X-Request-Id";
    /** Claves del MDC: aparecen en cada línea de log de la solicitud (también las de los servicios). */
    public static final String MDC_SOLICITUD = "solicitud";
    public static final String MDC_USUARIO = "usuario";
    /** Una solicitud que tarda más queda como advertencia: algo va lento (base, SUNAT, disco). */
    static final long LENTA_MS = 3_000;
    private static final Pattern CODIGO_VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String codigo = codigo(request.getHeader(CABECERA));
        long inicio = System.nanoTime();
        MDC.put(MDC_SOLICITUD, codigo);
        response.setHeader(CABECERA, codigo);
        try {
            chain.doFilter(request, response);
        } finally {
            registrar(request, response.getStatus(), (System.nanoTime() - inicio) / 1_000_000);
            MDC.clear();
        }
    }

    /** El código de la solicitud en curso (null fuera de una solicitud, ej. en un proceso programado). */
    public static String codigoActual() {
        return MDC.get(MDC_SOLICITUD);
    }

    private static String codigo(String recibido) {
        return recibido != null && CODIGO_VALIDO.matcher(recibido).matches() ? recibido
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static void registrar(HttpServletRequest request, int estado, long ms) {
        String ruta = request.getRequestURI();
        // La revisión de salud y las métricas se consultan cada pocos segundos: llenarían el log.
        if (ruta.startsWith("/actuator")) {
            return;
        }
        String usuario = MDC.get(MDC_USUARIO);
        String linea = "{} {} -> {} en {} ms (usuario {}, ip {})";
        Object[] datos = {request.getMethod(), ruta, estado, ms, usuario != null ? usuario : "-", request.getRemoteAddr()};
        if (estado >= 500 || ms >= LENTA_MS) {
            log.warn(linea + (ms >= LENTA_MS ? " LENTA" : ""), datos);
        } else {
            log.info(linea, datos);
        }
    }
}
