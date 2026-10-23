package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.contabilidad.service.RegistroVentasService;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.ReemitirRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

/**
 * Comprobantes rechazados por SUNAT (envío simulado): se reemiten con el siguiente número sin
 * volver a mover stock ni caja, o se anulan solo en el sistema.
 */
class ReemisionIntegrationTest extends TiendaDePrueba {

    private static final ServicioEnvioSunat.Respuesta ACEPTADA =
            new ServicioEnvioSunat.Respuesta(true, "0", "Aceptada", List.of(), new byte[]{1});
    private static final ServicioEnvioSunat.Respuesta RECHAZADA =
            new ServicioEnvioSunat.Respuesta(true, "2017", "El numero de documento de identidad del receptor debe ser RUC",
                    List.of(), new byte[]{1});

    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private EmpresaService empresaService;
    @Autowired private ImpresionService impresionService;
    @Autowired private RegistroVentasService registroVentasService;

    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    @BeforeEach
    void registrarEmpresa() throws Exception {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", null,
                    "Jr. Prueba 1", "150115", null, null, null));
        }
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(RECHAZADA);
    }

    @Test
    void laFacturaRechazadaSeReemiteComoBoletaSinMoverStockNiCaja() {
        VentaResponse factura = enviada(vender(TipoComprobante.FACTURA, serieFactura, clienteRuc,
                List.of(item(jeanA, 2, null)), List.of(efectivo("100.00", null), yape("79.80"))));
        int stockTrasVender = stock(jeanA, tienda);

        VentaResponse boleta = ventaService.reemitir(factura.id(),
                new ReemitirRequest(TipoComprobante.BOLETA, serieBoleta, clienteDni));

        assertThat(boleta.numero()).isEqualTo("B991-00000001");
        assertThat(boleta.reemplazaA()).isEqualTo(factura.numero());
        assertThat(boleta.cliente().numeroDocumento()).isEqualTo("41234567");
        assertThat(boleta.total()).isEqualByComparingTo("179.80");
        assertThat(boleta.detalles()).singleElement().satisfies(d -> assertThat(d.cantidad()).isEqualTo(2));
        assertThat(boleta.pagos()).extracting(VentaResponse.Pago::metodo).containsExactly(MetodoPago.EFECTIVO, MetodoPago.YAPE);
        assertThat(boleta.sunat().estado()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(boleta.cajaSesionId()).isNull();

        VentaResponse anterior = ventaService.obtenerDetalle(factura.id());
        assertThat(anterior.estado()).isEqualTo(EstadoComprobante.REEMPLAZADO);
        assertThat(anterior.reemplazadoPor()).isEqualTo(boleta.numero());
        assertThat(stock(jeanA, tienda)).isEqualTo(stockTrasVender);
        // La caja cuenta la venta una sola vez: la del rechazado, que es donde se cobró.
        var caja = cajaService.miCaja().resumen();
        assertThat(caja.cantidadVentas()).isEqualTo(1);
        assertThat(caja.totalVentas()).isEqualByComparingTo("179.80");
    }

    @Test
    void sinCambiosSeReemiteConLaMismaSerieYElSiguienteNumero() {
        VentaResponse boleta = enviada(boleta());

        VentaResponse nueva = ventaService.reemitir(boleta.id(), null);

        assertThat(nueva.numero()).isEqualTo("B991-00000002");
        assertThat(nueva.cliente().numeroDocumento()).isEqualTo(boleta.cliente().numeroDocumento());
        // Si la nueva también la rechaza SUNAT, se puede reemitir otra vez.
        assertThat(ventaService.reemitir(enviada(nueva).id(), null).numero()).isEqualTo("B991-00000003");
    }

    @Test
    void soloSeReemiteLoQueSunatRechazo() throws Exception {
        VentaResponse pendiente = boleta();
        assertThatThrownBy(() -> ventaService.reemitir(pendiente.id(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("aún no responde");

        when(servicioEnvio.enviar(anyString(), any())).thenReturn(ACEPTADA);
        VentaResponse aceptada = enviada(pendiente);
        assertThatThrownBy(() -> ventaService.reemitir(aceptada.id(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("SUNAT aceptó");

        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        assertThatThrownBy(() -> ventaService.reemitir(nota.id(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("documento interno");
    }

    @Test
    void noSeReemiteDosVecesNiSeLeEmiteNota() {
        VentaResponse boleta = enviada(boleta());
        assertThatThrownBy(() -> notaCreditoService.emitir(boleta.id(), devolucion()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no admite notas");

        ventaService.reemitir(boleta.id(), null);

        assertThatThrownBy(() -> ventaService.reemitir(boleta.id(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya se reemitió");
        assertThatThrownBy(() -> anulacionService.anular(boleta.id(), new AnularVentaRequest("prueba")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya se reemitió");
    }

    @Test
    void laFacturaSoloPasaABoletaIndicandoLaSerieYConClienteValido() {
        VentaResponse factura = enviada(vender(TipoComprobante.FACTURA, serieFactura, clienteRuc,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null))));

        assertThatThrownBy(() -> ventaService.reemitir(factura.id(), new ReemitirRequest(TipoComprobante.BOLETA, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Indica la serie de boleta");
        assertThatThrownBy(() -> ventaService.reemitir(factura.id(), new ReemitirRequest(null, null, clienteDni)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("La factura requiere un cliente con RUC");
    }

    @Test
    void laNotaDeCreditoRechazadaSeReemiteSinDevolverOtraVezLaMercaderia() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(ACEPTADA);
        when(servicioEnvio.enviar(contains("-07-"), any())).thenReturn(RECHAZADA);
        VentaResponse boleta = enviada(vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 2, null)),
                List.of(efectivo("179.80", null))));
        VentaResponse nota = enviada(notaCreditoService.emitir(boleta.id(), devolucion()));
        int stockTrasDevolver = stock(jeanA, tienda);

        VentaResponse nueva = ventaService.reemitir(nota.id(), null);

        assertThat(nueva.numero()).isEqualTo("BC91-00000002");
        assertThat(nueva.comprobanteOrigen()).isEqualTo(boleta.numero());
        assertThat(nueva.motivoNota()).isEqualTo(MotivoNotaCredito.DEVOLUCION_POR_ITEM);
        assertThat(nueva.total()).isEqualByComparingTo("89.90");
        assertThat(stock(jeanA, tienda)).isEqualTo(stockTrasDevolver);
        assertThat(ventaService.obtenerDetalle(boleta.id()).notasCredito()).containsExactly(nueva.numero());
        VentaResponse nuevaRechazada = enviada(nueva);
        assertThatThrownBy(() -> ventaService.reemitir(nuevaRechazada.id(), new ReemitirRequest(null, serieNcBoleta, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("misma serie");
    }

    @Test
    void unRechazadoSeAnulaSoloEnElSistemaSinBajaAnteSunat() {
        VentaResponse boleta = enviada(boleta());
        int stockTrasVender = stock(jeanA, tienda);

        VentaResponse anulada = anulacionService.anular(boleta.id(), new AnularVentaRequest("El cliente desistió"));

        assertThat(anulada.estado()).isEqualTo(EstadoComprobante.ANULADO);
        assertThat(anulada.bajaSunat()).isNull();
        assertThat(stock(jeanA, tienda)).isEqualTo(stockTrasVender + 1);
    }

    @Test
    void elPdfDelRechazadoLoAdvierteYElRegistroDeVentasYaNoPideNadaTrasReemitir() throws Exception {
        VentaResponse boleta = enviada(boleta());
        String pdf = new PdfTextExtractor(new PdfReader(impresionService.generar(boleta.id(), FormatoImpresion.A4).contenido()))
                .getTextFromPage(1);
        assertThat(pdf).contains("RECHAZADO POR SUNAT");

        YearMonth mes = YearMonth.now(JpaAuditingConfig.ZONA_LIMA);
        assertThat(registroVentasService.generar(mes).advertencias())
                .anyMatch(a -> a.contains("SUNAT rechazó " + boleta.numero()) && a.contains("reemítelo"));
        ventaService.reemitir(boleta.id(), null);
        assertThat(registroVentasService.generar(mes).advertencias()).noneMatch(a -> a.contains(boleta.numero() + ":"));
    }

    // ---------- Ayudas ----------

    private VentaResponse enviada(VentaResponse comprobante) {
        envioSunatService.enviarAhora(comprobante.id(), false);
        return ventaService.obtenerDetalle(comprobante.id());
    }

    private VentaResponse boleta() {
        return vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
    }

    private NotaCreditoRequest devolucion() {
        return new NotaCreditoRequest(serieNcBoleta, MotivoNotaCredito.DEVOLUCION_POR_ITEM, "Cambio de talla",
                MetodoPago.EFECTIVO, null, List.of(new ItemDevolucionRequest(jeanA, 1)));
    }

    private static com.allstore.api.venta.dto.PagoRequest yape(String monto) {
        return new com.allstore.api.venta.dto.PagoRequest(MetodoPago.YAPE, new java.math.BigDecimal(monto), null, "OP-1");
    }
}
