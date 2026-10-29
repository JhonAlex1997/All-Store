package com.allstore.api.seguridad.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos fallidos por IP en el ingreso y en el cambio de contraseña. El bloqueo por
 * usuario (5 intentos) no frena el password spraying, que prueba una misma clave contra muchos
 * usuarios distintos: este filtro cuenta los fallos de cada IP en una ventana de tiempo y, al
 * llegar al máximo, responde 429 sin siquiera revisar la contraseña (tampoco gasta el costo de
 * BCrypt).
 *
 * <p>La IP es la de la conexión (request.getRemoteAddr()). Detrás de un proxy inverso, activa
 * server.forward-headers-strategy=native para que sea la del cliente y no la del proxy.
 */
@Slf4j
public class LimiteIntentosLogin extends OncePerRequestFilter {

    static final Set<String> RUTAS = Set.of("/api/auth/login", "/api/auth/cambiar-password");
    /** Por encima de esta cantidad de IPs registradas se descartan las que ya no tienen fallos recientes. */
    private static final int LIMPIAR_DESDE = 10_000;

    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;
    private final RespuestaErrorJson respuestaErrorJson;
    private final Map<String, Deque<Instant>> fallos = new ConcurrentHashMap<>();

    public LimiteIntentosLogin(int maximo, Duration ventana, Clock reloj, RespuestaErrorJson respuestaErrorJson) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
        this.respuestaErrorJson = respuestaErrorJson;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !RUTAS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Instant ahora = reloj.instant();
        Deque<Instant> deIp = fallos.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (deIp) {
            descartarVencidos(deIp, ahora);
            if (deIp.size() >= maximo) {
                long segundos = Math.max(1, Duration.between(ahora, deIp.peekFirst().plus(ventana)).toSeconds());
                response.setHeader("Retry-After", String.valueOf(segundos));
                respuestaErrorJson.escribir(request, response, HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiados intentos fallidos desde esta conexión. Intenta de nuevo en "
                                + Math.max(1, (segundos + 59) / 60) + " minuto(s).");
                return;
            }
        }
        chain.doFilter(request, response);
        int estado = response.getStatus();
        if (estado == HttpStatus.UNAUTHORIZED.value() || estado == HttpStatus.BAD_REQUEST.value()) {
            synchronized (deIp) {
                deIp.addLast(ahora);
                if (deIp.size() == maximo) {
                    log.warn("La IP {} llegó a {} intentos fallidos de ingreso en {} minutos", ip, maximo, ventana.toMinutes());
                }
            }
        }
        if (fallos.size() > LIMPIAR_DESDE) {
            fallos.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    descartarVencidos(e.getValue(), ahora);
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    private void descartarVencidos(Deque<Instant> deIp, Instant ahora) {
        Instant limite = ahora.minus(ventana);
        while (!deIp.isEmpty() && !deIp.peekFirst().isAfter(limite)) {
            deIp.pollFirst();
        }
    }
}
