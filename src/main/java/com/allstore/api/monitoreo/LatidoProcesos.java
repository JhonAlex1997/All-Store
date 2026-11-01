package com.allstore.api.monitoreo;

import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.monitoreo.dto.EstadoProceso;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latido de los procesos programados (envíos a SUNAT, correos, WhatsApp): cuándo corrió cada uno
 * por última vez, cuánto tardó y si falló. Un proceso que dejó de correr no da ningún error: solo
 * deja de enviar. Por eso se compara su última vuelta con lo esperado y se avisa si se detuvo.
 */
@Slf4j
@Component
public class LatidoProcesos {

    public static final String ENVIO_SUNAT = "envio-sunat";
    public static final String CORREOS = "correos";
    public static final String WHATSAPP = "whatsapp";

    /** Corren cada 30 s: si en 10 minutos no terminaron una vuelta, están detenidos o colgados. */
    static final Duration DETENIDO_TRAS = Duration.ofMinutes(10);

    private final Map<String, Estado> procesos = new ConcurrentHashMap<>();
    private final RegistroErrores errores;
    private final MeterRegistry registry;
    private final Clock reloj;
    private final OffsetDateTime arranque;

    @Autowired
    public LatidoProcesos(RegistroErrores errores, MeterRegistry registry) {
        this(errores, registry, Clock.system(JpaAuditingConfig.ZONA_LIMA));
    }

    LatidoProcesos(RegistroErrores errores, MeterRegistry registry, Clock reloj) {
        this.errores = errores;
        this.registry = registry;
        this.reloj = reloj;
        this.arranque = OffsetDateTime.now(reloj);
    }

    /** Corre una vuelta del proceso y registra su latido; un fallo se registra y no se propaga. */
    public void ejecutar(String proceso, Runnable vuelta) {
        Estado estado = procesos.computeIfAbsent(proceso, p -> new Estado());
        estado.inicio = ahora();
        Timer.Sample muestra = Timer.start(registry);
        try {
            vuelta.run();
            estado.fin = ahora();
            estado.ultimoError = null;
        } catch (RuntimeException e) {
            estado.fin = ahora();
            estado.ultimoError = e.getClass().getSimpleName() + ": " + e.getMessage();
            estado.fallos++;
            errores.proceso(proceso, e);
            log.error("Falló una vuelta del proceso {}", proceso, e);
        } finally {
            muestra.stop(Timer.builder("allstore.procesos.vuelta").tag("proceso", proceso)
                    .description("Duración de cada vuelta de los procesos programados").register(registry));
        }
    }

    /** Estado de cada proceso conocido. Uno que nunca corrió desde el arranque también se reporta. */
    public List<EstadoProceso> estados() {
        OffsetDateTime ahora = ahora();
        return List.of(ENVIO_SUNAT, CORREOS, WHATSAPP).stream()
                .map(p -> estado(p, procesos.get(p), ahora))
                .sorted(Comparator.comparing(EstadoProceso::proceso))
                .toList();
    }

    public List<EstadoProceso> detenidos() {
        return estados().stream().filter(EstadoProceso::detenido).toList();
    }

    private EstadoProceso estado(String proceso, Estado e, OffsetDateTime ahora) {
        OffsetDateTime referencia = e == null || e.fin == null ? arranque : e.fin;
        boolean detenido = Duration.between(referencia, ahora).compareTo(DETENIDO_TRAS) > 0;
        if (e == null) {
            return new EstadoProceso(proceso, null, null, detenido, 0, null);
        }
        return new EstadoProceso(proceso, e.inicio, e.fin, detenido, e.fallos, e.ultimoError);
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj);
    }

    private static final class Estado {
        volatile OffsetDateTime inicio;
        volatile OffsetDateTime fin;
        volatile String ultimoError;
        volatile long fallos;
    }
}
