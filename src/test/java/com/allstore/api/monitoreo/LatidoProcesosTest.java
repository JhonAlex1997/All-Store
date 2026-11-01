package com.allstore.api.monitoreo;

import com.allstore.api.monitoreo.dto.EstadoProceso;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LatidoProcesosTest {

    private final RelojMovil reloj = new RelojMovil();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RegistroErrores errores = new RegistroErrores(registry, reloj);
    private final LatidoProcesos latido = new LatidoProcesos(errores, registry, reloj);

    @Test
    void unProcesoQueDejaDeCorrerSeReportaDetenido() {
        latido.ejecutar(LatidoProcesos.ENVIO_SUNAT, () -> { });
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(estado(LatidoProcesos.ENVIO_SUNAT).detenido()).isFalse();

        reloj.avanzar(Duration.ofMinutes(6));
        assertThat(latido.detenidos()).extracting(EstadoProceso::proceso).contains(LatidoProcesos.ENVIO_SUNAT);

        latido.ejecutar(LatidoProcesos.ENVIO_SUNAT, () -> { });
        assertThat(estado(LatidoProcesos.ENVIO_SUNAT).detenido()).isFalse();
    }

    @Test
    void unoQueNuncaCorrioDesdeElArranqueTambienSeDetecta() {
        assertThat(estado(LatidoProcesos.CORREOS).ultimoFin()).isNull();
        assertThat(estado(LatidoProcesos.CORREOS).detenido()).isFalse();
        reloj.avanzar(Duration.ofMinutes(11));
        assertThat(estado(LatidoProcesos.CORREOS).detenido()).isTrue();
    }

    @Test
    void unaVueltaQueFallaNoDetieneElProcesoYQuedaRegistrada() {
        assertThatCode(() -> latido.ejecutar(LatidoProcesos.WHATSAPP, () -> {
            throw new IllegalStateException("Meta no responde");
        })).doesNotThrowAnyException();

        EstadoProceso whatsapp = estado(LatidoProcesos.WHATSAPP);
        assertThat(whatsapp.fallos()).isEqualTo(1);
        assertThat(whatsapp.ultimoError()).contains("Meta no responde");
        assertThat(whatsapp.detenido()).isFalse();
        assertThat(errores.recientes()).singleElement().satisfies(e -> {
            assertThat(e.origen()).isEqualTo("proceso whatsapp");
            assertThat(e.solicitud()).isNull();
        });
        assertThat(registry.get("allstore.errores.internos").counter().count()).isEqualTo(1);

        // La siguiente vuelta bien limpia el último error (los fallos se siguen contando).
        latido.ejecutar(LatidoProcesos.WHATSAPP, () -> { });
        assertThat(estado(LatidoProcesos.WHATSAPP).ultimoError()).isNull();
        assertThat(estado(LatidoProcesos.WHATSAPP).fallos()).isEqualTo(1);
    }

    @Test
    void elRegistroDeErroresConservaSoloLosUltimos() {
        for (int i = 0; i < RegistroErrores.MAXIMO + 5; i++) {
            errores.proceso("correos", new RuntimeException("fallo " + i));
        }
        assertThat(errores.recientes()).hasSize(RegistroErrores.MAXIMO);
        assertThat(errores.recientes().getFirst().mensaje()).isEqualTo("fallo " + (RegistroErrores.MAXIMO + 4));
        reloj.avanzar(Duration.ofHours(25));
        assertThat(errores.desde(reloj.instant().atOffset(ZoneOffset.UTC).minusHours(24))).isZero();
    }

    private EstadoProceso estado(String proceso) {
        return latido.estados().stream().filter(p -> p.proceso().equals(proceso)).findFirst().orElseThrow();
    }

    /** Reloj que la prueba adelanta a voluntad. */
    static final class RelojMovil extends Clock {
        private Instant ahora = Instant.parse("2026-10-31T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Lima");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
