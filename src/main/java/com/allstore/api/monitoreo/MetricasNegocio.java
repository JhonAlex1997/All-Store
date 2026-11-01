package com.allstore.api.monitoreo;

import com.allstore.api.caja.entity.EstadoCaja;
import com.allstore.api.caja.repository.CajaSesionRepository;
import com.allstore.api.entrega.entity.EnvioWhatsapp;
import com.allstore.api.entrega.entity.EstadoCorreo;
import com.allstore.api.entrega.repository.EnvioCorreoRepository;
import com.allstore.api.entrega.repository.EnvioWhatsappRepository;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Indicadores del negocio para Prometheus/Grafana (además de los automáticos: solicitudes HTTP,
 * memoria, conexiones a la base): cuánto espera ser enviado a SUNAT, por correo y por WhatsApp,
 * cuánto quedó en error y cuántas cajas hay abiertas. Una cola que crece sin bajar es la señal de
 * que algo dejó de funcionar.
 *
 * <p>Cada valor se consulta a la base como máximo cada 30 segundos, aunque se lean más seguido.
 */
@Component
@RequiredArgsConstructor
public class MetricasNegocio implements MeterBinder {

    static final Duration VIGENCIA = Duration.ofSeconds(30);

    private final EnvioSunatRepository envioSunatRepository;
    private final EnvioCorreoRepository envioCorreoRepository;
    private final EnvioWhatsappRepository whatsappRepository;
    private final CajaSesionRepository cajaSesionRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        for (EstadoSunat estado : new EstadoSunat[]{EstadoSunat.PENDIENTE, EstadoSunat.EN_PROCESO, EstadoSunat.RECHAZADO, EstadoSunat.ERROR}) {
            indicador(registry, "allstore.sunat.envios", "Envíos a SUNAT por estado", "estado", estado.name(),
                    () -> envioSunatRepository.countByEstado(estado));
        }
        for (EstadoCorreo estado : new EstadoCorreo[]{EstadoCorreo.PENDIENTE, EstadoCorreo.ERROR}) {
            indicador(registry, "allstore.correos", "Correos al cliente por estado", "estado", estado.name(),
                    () -> envioCorreoRepository.countByEstado(estado));
        }
        for (EnvioWhatsapp.Estado estado : new EnvioWhatsapp.Estado[]{EnvioWhatsapp.Estado.PENDIENTE, EnvioWhatsapp.Estado.ERROR}) {
            indicador(registry, "allstore.whatsapp", "WhatsApp al cliente por estado", "estado", estado.name(),
                    () -> whatsappRepository.countByEstado(estado));
        }
        Gauge.builder("allstore.cajas.abiertas", new Memoria(() -> cajaSesionRepository.countByEstado(EstadoCaja.ABIERTA)),
                        Memoria::valor)
                .description("Cajas abiertas ahora").register(registry);
    }

    private static void indicador(MeterRegistry registry, String nombre, String descripcion, String etiqueta, String valor,
                                  LongSupplier consulta) {
        Gauge.builder(nombre, new Memoria(consulta), Memoria::valor)
                .description(descripcion).tag(etiqueta, valor).register(registry);
    }

    /** Recuerda el último valor durante {@link #VIGENCIA}; si la base no responde, da NaN (sin dato). */
    static final class Memoria {
        private final LongSupplier consulta;
        private double ultimo = Double.NaN;
        private long leidoEn;

        Memoria(LongSupplier consulta) {
            this.consulta = consulta;
        }

        synchronized double valor() {
            long ahora = System.nanoTime();
            if (Double.isNaN(ultimo) || ahora - leidoEn > VIGENCIA.toNanos()) {
                try {
                    ultimo = consulta.getAsLong();
                } catch (RuntimeException e) {
                    ultimo = Double.NaN;
                }
                leidoEn = ahora;
            }
            return ultimo;
        }
    }
}
