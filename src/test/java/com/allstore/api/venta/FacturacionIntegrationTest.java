package com.allstore.api.venta;

import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.venta.dto.VentaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integración ventas ↔ SUNAT con el envío simulado (sin red): estados según la respuesta y
 * reintentos. El envío real a SUNAT beta se prueba con la aplicación en el perfil dev.
 */
class FacturacionIntegrationTest extends TiendaDePrueba {

    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private EmpresaService empresaService;
    @Autowired private ImpresionService impresionService;

    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    @BeforeEach
    void registrarEmpresa() {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", null,
                    "Jr. Prueba 1", "150115", null, null));
        }
    }

    @Test
    void alEmitirUnaBoletaQuedaPendienteConXmlFirmadoHashYQr() {
        VentaResponse boleta = boleta();

        assertThat(boleta.sunat().estado()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(boleta.sunat().hash()).isNotBlank();
        assertThat(boleta.sunat().codigoQr())
                .startsWith("20000000001|03|B991|1|13.71|89.90|")
                .endsWith(boleta.sunat().hash() + "|");
    }

    @Test
    void laNotaDeVentaNoSeEnviaASunat() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
        assertThat(nota.sunat()).isNull();
    }

    @Test
    void cdrConCodigoCeroLoDejaAceptado() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(
                new ServicioEnvioSunat.Respuesta(true, "0", "La Boleta numero B991-1, ha sido aceptada", List.of(), new byte[]{1}));
        VentaResponse boleta = boleta();

        var envio = envioSunatService.enviarAhora(boleta.id(), false);

        assertThat(envio.estado()).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(envio.nombreArchivo()).isEqualTo("20000000001-03-B991-1");
        assertThat(envio.tieneCdr()).isTrue();
    }

    @Test
    void cdrConCodigoDeRechazoLoDejaRechazado() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(
                new ServicioEnvioSunat.Respuesta(true, "2800", "Dato inválido", List.of(), new byte[]{1}));

        assertThat(envioSunatService.enviarAhora(boleta().id(), false).estado()).isEqualTo(EstadoSunat.RECHAZADO);
    }

    @Test
    void sinComunicacionQuedaPendienteParaReintentar() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenThrow(new IOException("Connection refused"));

        var envio = envioSunatService.enviarAhora(boleta().id(), false);

        assertThat(envio.estado()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(envio.intentos()).isEqualTo(1);
        assertThat(envio.proximoIntento()).isNotNull();
        assertThat(envio.descripcionRespuesta()).contains("Sin comunicación");
    }

    @Test
    void imprimeLaBoletaYMarcaComoAnuladaLaNotaDeVentaAnulada() throws Exception {
        VentaResponse boleta = boleta();
        byte[] ticket = impresionService.generar(boleta.id(), FormatoImpresion.TICKET).contenido();
        assertThat(textoPdf(ticket)).contains(boleta.numero(), "Hash: " + boleta.sunat().hash(), "PRUEBA - SIN VALIDEZ");

        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
        ventaService.anular(nota.id(), new com.allstore.api.venta.dto.AnularVentaRequest("prueba"));
        String a4 = textoPdf(impresionService.generar(nota.id(), FormatoImpresion.A4).contenido());
        assertThat(a4).contains("NOTA DE VENTA", "ANULADO", "No es un comprobante de pago");
    }

    private static String textoPdf(byte[] pdf) throws Exception {
        return new org.openpdf.text.pdf.parser.PdfTextExtractor(new org.openpdf.text.pdf.PdfReader(pdf)).getTextFromPage(1);
    }

    private VentaResponse boleta() {
        return vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
    }
}
