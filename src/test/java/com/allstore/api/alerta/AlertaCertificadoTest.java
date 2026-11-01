package com.allstore.api.alerta;

import com.allstore.api.alerta.config.AlertaProperties;
import com.allstore.api.alerta.dto.Alerta;
import com.allstore.api.alerta.dto.Alerta.Nivel;
import com.allstore.api.alerta.dto.Alerta.Tipo;
import com.allstore.api.alerta.service.AlertaService;
import com.allstore.api.monitoreo.AlertasSistema;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.facturacion.repository.ResumenSunatRepository;
import com.allstore.api.facturacion.service.FirmadorXml;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Alertas del certificado digital (sin base: sin comprobantes pendientes). */
class AlertaCertificadoTest {

    private final FirmadorXml firmador = mock(FirmadorXml.class);
    /** Sin envíos: toda consulta de la base responde vacío. */
    private final EnvioSunatRepository envios = mock(EnvioSunatRepository.class,
            i -> i.getMethod().getReturnType() == Page.class ? Page.empty() : null);

    @Test
    void avisaUnMesAntesDeQueVenza() {
        when(firmador.vencimiento()).thenReturn(Optional.of(Instant.now().plus(10, ChronoUnit.DAYS)));

        assertThat(alertas(true)).singleElement().satisfies(a -> {
            assertThat(a.tipo()).isEqualTo(Tipo.CERTIFICADO);
            assertThat(a.nivel()).isEqualTo(Nivel.ADVERTENCIA);
            assertThat(a.mensaje()).contains("vence el", "días)");
        });
    }

    @Test
    void conMasTiempoNoAvisa() {
        when(firmador.vencimiento()).thenReturn(Optional.of(Instant.now().plus(200, ChronoUnit.DAYS)));

        assertThat(alertas(true)).isEmpty();
    }

    @Test
    void vencidoOFaltanteEsCritico() {
        when(firmador.vencimiento()).thenReturn(Optional.of(Instant.now().minus(1, ChronoUnit.DAYS)));
        assertThat(alertas(true)).singleElement().satisfies(a -> {
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICA);
            assertThat(a.mensaje()).contains("venció");
        });

        when(firmador.vencimiento()).thenReturn(Optional.empty());
        assertThat(alertas(true)).singleElement().satisfies(a -> assertThat(a.mensaje()).contains("Falta el certificado"));
        // Sin envío a SUNAT, la falta de certificado no es la alerta: lo es el envío desactivado.
        assertThat(alertas(false)).extracting(Alerta::tipo).containsExactly(Tipo.SUNAT_DESACTIVADO);
    }

    private List<Alerta> alertas(boolean sunatHabilitado) {
        SunatProperties sunat = new SunatProperties(sunatHabilitado, EntornoSunat.PRODUCCION, null, null, "USUARIO", "clave",
                null, null, 30000, 8, null);
        AlertaService servicio = new AlertaService(envios, mock(ResumenSunatRepository.class), firmador, sunat,
                new AlertaProperties(null, "0 0 8 * * *", 3, 30), mock(AlertasSistema.class));
        return servicio.alertas().alertas();
    }
}
