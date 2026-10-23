package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.facturacion.repository.ResumenSunatRepository;
import com.allstore.api.facturacion.service.BajaSunatService;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Anulación de comprobantes electrónicos: reglas, efectos en stock y caja, y la baja ante SUNAT
 * (comunicación de baja o resumen diario) con el envío simulado.
 */
class AnulacionElectronicaIntegrationTest extends TiendaDePrueba {

    private static final ServicioEnvioSunat.Respuesta ACEPTADO =
            new ServicioEnvioSunat.Respuesta(true, "0", "Aceptado", List.of(), new byte[]{1});

    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private BajaSunatService bajaSunatService;
    @Autowired private EnvioSunatRepository envioRepository;
    @Autowired private ResumenSunatRepository resumenRepository;
    @Autowired private EmpresaService empresaService;

    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    private String hoy;

    @BeforeEach
    void prepararSunat() throws Exception {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", null,
                    "Jr. Prueba 1", "150115", null, null, null));
        }
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(ACEPTADO);
        hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @Test
    void anularBoletaAceptadaDevuelveStockYLaInformaAnuladaEnResumenDiario() {
        VentaResponse boleta = aceptada(vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null))));
        assertThat(stock(jeanA, tienda)).isEqualTo(3);

        VentaResponse anulada = anulacionService.anular(boleta.id(), new AnularVentaRequest("Error en la venta"));

        assertThat(anulada.estado()).isEqualTo(EstadoComprobante.ANULADO);
        assertThat(stock(jeanA, tienda)).isEqualTo(5);
        assertThat(cajaService.miCaja().resumen().totalVentas()).isEqualByComparingTo("0.00");
        assertThat(anulada.sunat().estado()).as("la boleta sigue aceptada; lo que cambia es su baja").isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(anulada.bajaSunat().documento()).isEqualTo("Resumen diario");
        assertThat(anulada.bajaSunat().numero()).startsWith("RC-" + hoy + "-");
        assertThat(anulada.bajaSunat().estado()).isEqualTo(EstadoSunat.PENDIENTE);

        String xml = xmlDeLaBaja(boleta.id());
        assertThat(xml).contains("<SummaryDocuments", "<cbc:ID>B991-1</cbc:ID>",
                "<cbc:ConditionCode>3</cbc:ConditionCode>", "<sac:TotalAmount currencyID=\"PEN\">179.80</sac:TotalAmount>");
    }

    @Test
    void anularFacturaLaDaDeBajaConComunicacionDeBaja() {
        VentaResponse factura = aceptada(vender(TipoComprobante.FACTURA, serieFactura, clienteRuc,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null))));

        VentaResponse anulada = anulacionService.anular(factura.id(), new AnularVentaRequest("RUC equivocado"));

        assertThat(anulada.bajaSunat().documento()).isEqualTo("Comunicación de baja");
        assertThat(anulada.bajaSunat().numero()).startsWith("RA-" + hoy + "-");
        assertThat(xmlDeLaBaja(factura.id())).contains("<VoidedDocuments", "<sac:DocumentSerialID>F991</sac:DocumentSerialID>",
                "<sac:DocumentNumberID>1</sac:DocumentNumberID>", "<sac:VoidReasonDescription>RUC equivocado</sac:VoidReasonDescription>");
    }

    @Test
    void laBajaQuedaEnProcesoConSuTicketHastaQueSunatResponde() throws Exception {
        VentaResponse boleta = aceptada(boletaDeUno());
        anulacionService.anular(boleta.id(), new AnularVentaRequest("Error"));
        Long envioBaja = envioDeLaBaja(boleta.id()).getId();
        when(servicioEnvio.enviarResumen(anyString(), any())).thenReturn(new ServicioEnvioSunat.Ticket("1761234567890", null));
        when(servicioEnvio.consultarTicket(anyString(), anyString()))
                .thenReturn(ServicioEnvioSunat.EstadoTicket.procesando())
                .thenReturn(ServicioEnvioSunat.EstadoTicket.terminado(ACEPTADO));

        var enviado = envioSunatService.enviarEnvioAhora(envioBaja, false);
        assertThat(enviado.estado()).isEqualTo(EstadoSunat.EN_PROCESO);
        assertThat(enviado.ticket()).isEqualTo("1761234567890");

        assertThat(envioSunatService.enviarEnvioAhora(envioBaja, false).estado()).isEqualTo(EstadoSunat.EN_PROCESO);
        assertThat(envioSunatService.enviarEnvioAhora(envioBaja, false).estado()).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(ventaService.obtenerDetalle(boleta.id()).bajaSunat().estado()).isEqualTo(EstadoSunat.ACEPTADO);
    }

    @Test
    void siSunatRechazaLaBajaSeGeneraOtraConNuevoNumero() throws Exception {
        VentaResponse boleta = aceptada(boletaDeUno());
        VentaResponse anulada = anulacionService.anular(boleta.id(), new AnularVentaRequest("Error"));
        assertThatThrownBy(() -> bajaSunatService.reintentar(boleta.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("está PENDIENTE");

        when(servicioEnvio.enviarResumen(anyString(), any())).thenReturn(new ServicioEnvioSunat.Ticket(null,
                new ServicioEnvioSunat.Respuesta(false, "2992", "Tasa del tributo faltante", List.of(), null)));
        assertThat(envioSunatService.enviarEnvioAhora(envioDeLaBaja(boleta.id()).getId(), false).estado())
                .isEqualTo(EstadoSunat.RECHAZADO);

        var nueva = bajaSunatService.reintentar(boleta.id());

        String anterior = anulada.bajaSunat().numero();
        int n = Integer.parseInt(anterior.substring(anterior.lastIndexOf('-') + 1));
        assertThat(nueva.numero()).isEqualTo("RC-" + hoy + "-" + (n + 1));
        assertThat(nueva.estado()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(ventaService.obtenerDetalle(boleta.id()).bajaSunat().numero()).isEqualTo(nueva.numero());
    }

    @Test
    void cadaBajaDelDiaTomaElSiguienteCorrelativo() {
        VentaResponse primera = anulacionService.anular(aceptada(boletaDeUno()).id(), new AnularVentaRequest("Error 1"));
        VentaResponse segunda = anulacionService.anular(aceptada(boletaDeUno()).id(), new AnularVentaRequest("Error 2"));

        int n1 = Integer.parseInt(primera.bajaSunat().numero().substring(primera.bajaSunat().numero().lastIndexOf('-') + 1));
        assertThat(segunda.bajaSunat().numero()).isEqualTo("RC-" + hoy + "-" + (n1 + 1));
    }

    @Test
    void noSeDaDeBajaFueraDelPlazoDeSieteDias() {
        VentaResponse boleta = aceptada(boletaDeUno());
        EnvioSunat envio = envioRepository.findByComprobanteId(boleta.id()).orElseThrow();
        envio.setFechaRespuesta(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA).minusDays(8));

        assertThatThrownBy(() -> anulacionService.anular(boleta.id(), new AnularVentaRequest("tarde")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Venció el plazo")
                .hasMessageContaining("nota de crédito");
    }

    @Test
    void noSeDaDeBajaUnaBoletaConNotasDeCreditoVigentes() {
        VentaResponse boleta = aceptada(vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null))));
        VentaResponse nota = devolverUno(boleta);

        assertThatThrownBy(() -> anulacionService.anular(boleta.id(), new AnularVentaRequest("error")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tiene notas vigentes (" + nota.numero() + ")");
    }

    @Test
    void anularUnaNotaDeCreditoSacaOtraVezLaMercaderia() {
        VentaResponse boleta = aceptada(vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null))));
        VentaResponse nota = aceptada(devolverUno(boleta));
        assertThat(stock(jeanA, tienda)).isEqualTo(4);

        VentaResponse anulada = anulacionService.anular(nota.id(), new AnularVentaRequest("Nota emitida por error"));

        assertThat(stock(jeanA, tienda)).isEqualTo(3);
        assertThat(anulada.bajaSunat().documento()).isEqualTo("Resumen diario");
        assertThat(xmlDeLaBaja(nota.id())).contains("<cbc:DocumentTypeCode>07</cbc:DocumentTypeCode>",
                "<cac:InvoiceDocumentReference><cbc:ID>B991-1</cbc:ID>");
        // Sin la nota, la boleta vuelve a admitir la devolución completa.
        assertThat(ventaService.obtenerDetalle(boleta.id()).notasCredito()).isEmpty();
    }

    @Test
    void noSeDaDeBajaLoQueSunatAunNoAcepto() {
        VentaResponse boleta = boletaDeUno();
        assertThatThrownBy(() -> anulacionService.anular(boleta.id(), new AnularVentaRequest("error")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SUNAT aún no recibe");
    }

    // ---------- Ayudas ----------

    private VentaResponse boletaDeUno() {
        return vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)));
    }

    private VentaResponse devolverUno(VentaResponse boleta) {
        return notaCreditoService.emitir(boleta.id(), new NotaCreditoRequest(serieNcBoleta, MotivoNotaCredito.DEVOLUCION_POR_ITEM,
                "Cambio de talla", MetodoPago.EFECTIVO, null, List.of(new ItemDevolucionRequest(jeanA, 1))));
    }

    /** Simula que SUNAT aceptó el comprobante. */
    private VentaResponse aceptada(VentaResponse comprobante) {
        envioSunatService.enviarAhora(comprobante.id(), false);
        return ventaService.obtenerDetalle(comprobante.id());
    }

    private EnvioSunat envioDeLaBaja(Long comprobanteId) {
        Long resumenId = resumenRepository.ultimaBaja(comprobanteId).orElseThrow().getResumen().getId();
        return envioRepository.findByResumenId(resumenId).orElseThrow();
    }

    private String xmlDeLaBaja(Long comprobanteId) {
        return new String(envioSunatService.xmlDeEnvio(envioDeLaBaja(comprobanteId).getId()).contenido(), StandardCharsets.UTF_8);
    }
}
