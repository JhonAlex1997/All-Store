package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.venta.dto.VentaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Se pierde la respuesta de un envío que SUNAT sí registró: el reintento responde 1033 y la
 * constancia se pide al servicio de consulta (configurado como en producción; SUNAT simulado).
 */
@TestPropertySource(properties = "app.sunat.url-consulta=http://consulta.sunat.prueba/billConsultService")
class ConstanciaRecuperadaIntegrationTest extends TiendaDePrueba {

    private static final ServicioEnvioSunat.Respuesta YA_REGISTRADO = new ServicioEnvioSunat.Respuesta(false, "1033",
            "El comprobante fue registrado previamente con otros datos", List.of(), null);

    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private EmpresaService empresaService;

    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    private VentaResponse boleta;
    /** La constancia de esta boleta, como la devuelve la consulta de SUNAT. */
    private ServicioEnvioSunat.Respuesta constancia;

    @BeforeEach
    void boletaEmitida() throws Exception {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", null,
                    "Jr. Prueba 1", "150115", null, null, null));
        }
        boleta = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
        constancia = constancia("B991-1", boleta.cliente().numeroDocumento());
    }

    @Test
    void trasPerderLaRespuestaRecuperaLaConstanciaYQuedaAceptado() throws Exception {
        when(servicioEnvio.enviar(anyString(), any()))
                .thenThrow(new IOException("Read timed out"))
                .thenReturn(YA_REGISTRADO);
        when(servicioEnvio.consultarCdr(anyString(), anyString(), anyString(), anyLong())).thenReturn(constancia);

        assertThat(envioSunatService.enviarAhora(boleta.id(), false).estado()).isEqualTo(EstadoSunat.PENDIENTE);
        // Sin respuesta, SUNAT pudo recibirlo: regenerar el XML lo dejaría distinto del que tiene SUNAT.
        assertThatThrownBy(() -> envioSunatService.enviarAhora(boleta.id(), true))
                .isInstanceOf(BusinessException.class).hasMessageContaining("quedó sin respuesta");
        var envio = envioSunatService.enviarAhora(boleta.id(), false);

        assertThat(envio.estado()).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(envio.codigoRespuesta()).isEqualTo("0");
        assertThat(envio.descripcionRespuesta()).contains("ha sido aceptada", "constancia recuperada");
        assertThat(envio.tieneCdr()).isTrue();
        verify(servicioEnvio).consultarCdr("20000000001", "03", "B991", 1L);
    }

    @Test
    void siLaConsultaFallaSeReintentaLaConsultaYNoElEnvio() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(YA_REGISTRADO);
        when(servicioEnvio.consultarCdr(anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new IOException("Connection reset"))
                .thenReturn(new ServicioEnvioSunat.Respuesta(false, "0011", "El comprobante no existe", List.of(), null))
                .thenReturn(constancia);

        var primero = envioSunatService.enviarAhora(boleta.id(), false);
        assertThat(primero.estado()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(primero.descripcionRespuesta()).contains("ya tiene registrado", "no se pudo obtener su constancia");
        assertThat(primero.proximoIntento()).isNotNull();

        var segundo = envioSunatService.enviarAhora(boleta.id(), false);
        assertThat(segundo.estado()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(segundo.descripcionRespuesta()).contains("no entrega la constancia: 0011");

        assertThat(envioSunatService.enviarAhora(boleta.id(), false).estado()).isEqualTo(EstadoSunat.ACEPTADO);
        verify(servicioEnvio, times(1)).enviar(anyString(), any());
        verify(servicioEnvio, times(3)).consultarCdr(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void yaRegistradoNoSeRegeneraElXml() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(YA_REGISTRADO);
        when(servicioEnvio.consultarCdr(anyString(), anyString(), anyString(), anyLong())).thenThrow(new IOException("caído"));
        envioSunatService.enviarAhora(boleta.id(), false);

        assertThatThrownBy(() -> envioSunatService.enviarAhora(boleta.id(), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no se regenera su XML");
    }

    @Test
    void siElNumeroLoTieneOtroDocumentoQuedaRechazadoParaReemitirlo() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(YA_REGISTRADO);
        when(servicioEnvio.consultarCdr(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(constancia("B991-1", "41234567"));

        var envio = envioSunatService.enviarAhora(boleta.id(), false);

        assertThat(envio.estado()).isEqualTo(EstadoSunat.RECHAZADO);
        assertThat(envio.descripcionRespuesta()).contains("registrado para otro documento", "es del cliente 41234567");
        assertThat(envio.tieneCdr()).isFalse();
        assertThat(ventaService.reemitir(boleta.id(), null).numero()).isEqualTo("B991-00000002");
    }

    @Test
    void otrasExcepcionesSiguenReenviandose() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(new ServicioEnvioSunat.Respuesta(false, "0109",
                "El sistema no puede responder su solicitud", List.of(), null));

        assertThat(envioSunatService.enviarAhora(boleta.id(), false).estado()).isEqualTo(EstadoSunat.PENDIENTE);
        envioSunatService.enviarAhora(boleta.id(), false);

        verify(servicioEnvio, times(2)).enviar(anyString(), any());
        verify(servicioEnvio, never()).consultarCdr(anyString(), anyString(), anyString(), anyLong());
    }

    /** Constancia de aceptación en zip, con el número y el cliente como los escribe SUNAT ("tipo-numero"). */
    private static ServicioEnvioSunat.Respuesta constancia(String numero, String documentoCliente) throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"                 xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"                 xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">                <cac:DocumentResponse><cac:Response><cbc:ReferenceID>%1$s</cbc:ReferenceID>                <cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>La Boleta numero %1$s, ha sido aceptada</cbc:Description>                </cac:Response><cac:DocumentReference><cbc:ID>%1$s</cbc:ID></cac:DocumentReference>                <cac:RecipientParty><cac:PartyIdentification><cbc:ID>6-%2$s</cbc:ID></cac:PartyIdentification>                </cac:RecipientParty></cac:DocumentResponse></ar:ApplicationResponse>""".formatted(numero, documentoCliente);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream salida = new ZipOutputStream(zip)) {
            salida.putNextEntry(new ZipEntry("R-20000000001-03-" + numero + ".xml"));
            salida.write(xml.getBytes(StandardCharsets.UTF_8));
            salida.closeEntry();
        }
        return new ServicioEnvioSunat.Respuesta(true, "0", "La Boleta numero " + numero + ", ha sido aceptada",
                List.of(), zip.toByteArray());
    }
}
