package com.allstore.api.monitoreo;

import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.monitoreo.dto.ErrorRegistrado;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Los últimos errores internos (500 de la API y fallos de los procesos programados), para verlos
 * desde el panel sin entrar al servidor y para avisar al administrador. El detalle completo (la
 * traza) está en el log, con el mismo código de solicitud.
 *
 * <p>Vive en memoria: se reinicia con la aplicación. Es una vista rápida, no el registro oficial.
 */
@Component
public class RegistroErrores {

    static final int MAXIMO = 100;
    private static final int LARGO_MENSAJE = 300;

    private final Deque<ErrorRegistrado> recientes = new ArrayDeque<>();
    private final Counter contador;
    private final Clock reloj;

    @Autowired
    public RegistroErrores(MeterRegistry registry) {
        this(registry, Clock.system(JpaAuditingConfig.ZONA_LIMA));
    }

    RegistroErrores(MeterRegistry registry, Clock reloj) {
        this.contador = Counter.builder("allstore.errores.internos")
                .description("Errores internos: respuestas 500 y fallos de procesos programados")
                .register(registry);
        this.reloj = reloj;
    }

    /** Un error 500 de la API. */
    public void solicitud(String metodo, String ruta, Throwable error) {
        agregar(new ErrorRegistrado(ahora(), RegistroSolicitudes.codigoActual(), metodo + " " + ruta,
                error.getClass().getSimpleName(), mensaje(error)));
    }

    /** Un fallo en un proceso programado (envíos a SUNAT, correos, WhatsApp). */
    public void proceso(String proceso, Throwable error) {
        agregar(new ErrorRegistrado(ahora(), null, "proceso " + proceso, error.getClass().getSimpleName(), mensaje(error)));
    }

    /** Del más reciente al más antiguo. */
    public synchronized List<ErrorRegistrado> recientes() {
        return List.copyOf(recientes);
    }

    /** Cuántos hubo desde ese momento (entre los que se conservan). */
    public synchronized long desde(OffsetDateTime momento) {
        return recientes.stream().filter(e -> e.fecha().isAfter(momento)).count();
    }

    private synchronized void agregar(ErrorRegistrado error) {
        recientes.addFirst(error);
        while (recientes.size() > MAXIMO) {
            recientes.removeLast();
        }
        contador.increment();
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj);
    }

    private static String mensaje(Throwable error) {
        String m = error.getMessage() != null ? error.getMessage() : "(sin mensaje)";
        return m.length() > LARGO_MENSAJE ? m.substring(0, LARGO_MENSAJE) + "…" : m;
    }
}
