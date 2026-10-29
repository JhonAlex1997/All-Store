package com.allstore.api.venta;

import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.entrega.dto.EnvioWhatsappResponse;
import com.allstore.api.entrega.service.EntregaService;
import com.allstore.api.entrega.entity.EnvioWhatsapp;
import com.allstore.api.entrega.repository.EnvioCorreoRepository;
import com.allstore.api.entrega.repository.EnvioWhatsappRepository;
import com.allstore.api.entrega.service.ServicioWhatsapp;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Comprobante por WhatsApp: con la API de Meta (simulada) o, sin ella, con el enlace wa.me. */
class WhatsappIntegrationTest extends TiendaDePrueba {

    @Autowired private EntregaService entregaService;
    @Autowired private EmpresaService empresaService;

    @Autowired private EnvioWhatsappRepository whatsappRepository;
    @Autowired private EnvioCorreoRepository envioCorreoRepository;
    @Autowired private EnvioSunatService envioSunatService;

    @MockitoBean private ServicioWhatsapp servicioWhatsapp;
    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    private VentaResponse boleta;

    @BeforeEach
    void ventaConCelular() {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", "Jeans Prueba",
                    "Jr. Prueba 1", "150115", null, "ventas@prueba.pe", null));
        }
        Long cliente = clienteService.crear(new ClienteRequest(TipoDocumentoIdentidad.DNI, "45678912", "Rosa Prueba",
                null, null, "987 654 321", null, true)).id();
        boleta = vender(TipoComprobante.BOLETA, serieBoleta, cliente, List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
    }

    @Test
    void sinLaApiDevuelveElEnlaceParaEnviarloAMano() {
        EnvioWhatsappResponse r = entregaService.enviarWhatsapp(boleta.id(), null);

        assertThat(r.modo()).isEqualTo("ENLACE");
        assertThat(r.telefono()).isEqualTo("51987654321");
        assertThat(r.enlace()).startsWith("https://wa.me/51987654321?text=Hola%2C%20te%20enviamos%20tu%20boleta");
    }

    @Test
    void conLaApiLoEnviaElSistemaConElPdfYQuedaRegistrado() throws Exception {
        when(servicioWhatsapp.configurado()).thenReturn(true);
        when(servicioWhatsapp.enviar(anyString(), anyString(), anyString(), anyList())).thenReturn("wamid.123");

        EnvioWhatsappResponse r = entregaService.enviarWhatsapp(boleta.id(), "+51 912 345 678");

        assertThat(r.modo()).isEqualTo("AUTOMATICO");
        assertThat(r.estado()).isEqualTo("ENVIADO");
        assertThat(r.mensajeId()).isEqualTo("wamid.123");
        verify(servicioWhatsapp).enviar(eq("51912345678"), org.mockito.ArgumentMatchers.contains("/api/publico/comprobantes/"),
                eq(boleta.numero() + ".pdf"), eq(List.of("Rosa Prueba", "boleta de venta " + boleta.numero(), "S/ 89.90", "Jeans Prueba")));
        assertThat(entregaService.entrega(boleta.id()).whatsapps()).singleElement()
                .satisfies(w -> assertThat(w.estado()).isEqualTo("ENVIADO"));
    }

    @Test
    void siMetaFallaQuedaElErrorYElEnlaceDeRespaldo() throws Exception {
        when(servicioWhatsapp.configurado()).thenReturn(true);
        when(servicioWhatsapp.enviar(anyString(), anyString(), anyString(), any())).thenThrow(new IOException("WhatsApp no aceptó el mensaje: plantilla"));

        EnvioWhatsappResponse r = entregaService.enviarWhatsapp(boleta.id(), null);

        assertThat(r.estado()).isEqualTo("ERROR");
        assertThat(r.error()).contains("plantilla");
        assertThat(r.enlace()).startsWith("https://wa.me/51987654321");
    }

    @Test
    void laBoletaConWhatsappAlEmitirQuedaEnColaHastaQueSunatLaAcepte() throws Exception {
        when(servicioWhatsapp.configurado()).thenReturn(true);
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(
                new ServicioEnvioSunat.Respuesta(true, "0", "Aceptada", List.of(), new byte[]{1}));
        VentaResponse venta = ventaService.crear(new VentaRequest(TipoComprobante.BOLETA, serieBoleta, null, null,
                List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)), null, "912 345 678", "comprador@correo.pe"));

        assertThat(venta.whatsappEntrega()).isEqualTo("51912345678");
        assertThat(whatsappRepository.findByComprobanteIdOrderByIdDesc(venta.id())).as("aún no la acepta SUNAT").isEmpty();

        envioSunatService.enviarAhora(venta.id(), false);

        assertThat(whatsappRepository.findByComprobanteIdOrderByIdDesc(venta.id())).singleElement().satisfies(w -> {
            assertThat(w.getEstado()).isEqualTo(EnvioWhatsapp.Estado.PENDIENTE);
            assertThat(w.getTelefono()).isEqualTo("51912345678");
            assertThat(w.isAutomatico()).isTrue();
        });
        // El correo elegido al emitir también, aunque el cliente (Clientes varios) no tenga correo.
        assertThat(envioCorreoRepository.findByComprobanteIdOrderByIdDesc(venta.id())).singleElement()
                .satisfies(e -> assertThat(e.getDestinatario()).isEqualTo("comprador@correo.pe"));
    }

    @Test
    void laNotaDeVentaNoEsperaASunatYSinLaApiQuedaElEnlaceAlNumeroElegido() {
        when(servicioWhatsapp.configurado()).thenReturn(true);
        VentaResponse nota = ventaService.crear(new VentaRequest(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, null,
                List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)), null, "912345678", null));
        assertThat(whatsappRepository.findByComprobanteIdOrderByIdDesc(nota.id())).singleElement()
                .satisfies(w -> assertThat(w.getEstado()).isEqualTo(EnvioWhatsapp.Estado.PENDIENTE));

        when(servicioWhatsapp.configurado()).thenReturn(false);
        VentaResponse otra = ventaService.crear(new VentaRequest(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, null,
                List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)), null, "912345678", null));
        assertThat(whatsappRepository.findByComprobanteIdOrderByIdDesc(otra.id())).isEmpty();
        assertThat(entregaService.entrega(otra.id()).whatsapp()).startsWith("https://wa.me/51912345678?text=");
    }

    @Test
    void unCelularInvalidoAlEmitirSeRechazaAntesDeVender() {
        assertThatThrownBy(() -> ventaService.crear(new VentaRequest(TipoComprobante.BOLETA, serieBoleta, null, null,
                List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)), null, "123", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no es válido");
    }

    @Test
    void celularInvalidoYTopeDiario() throws Exception {
        assertThatThrownBy(() -> entregaService.enviarWhatsapp(boleta.id(), "12345"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no es válido");

        when(servicioWhatsapp.configurado()).thenReturn(true);
        when(servicioWhatsapp.enviar(anyString(), anyString(), anyString(), anyList())).thenReturn("wamid.x");
        for (int i = 0; i < 5; i++) {
            entregaService.enviarWhatsapp(boleta.id(), null);
        }
        assertThatThrownBy(() -> entregaService.enviarWhatsapp(boleta.id(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("es el máximo");
    }
}
