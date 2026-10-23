package com.allstore.api.venta;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.guia.dto.GuiaRemisionRequest;
import com.allstore.api.guia.dto.GuiaRemisionResponse;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.guia.entity.MotivoTraslado;
import com.allstore.api.guia.service.GuiaRemisionService;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.venta.dto.VentaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guías de remisión: traslado entre locales (mueve stock) y entrega de ventas (ver TiendaDePrueba). */
class GuiaRemisionIntegrationTest extends TiendaDePrueba {

    @Autowired private GuiaRemisionService guiaService;
    @Autowired private EmpresaService empresaService;
    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private EnvioSunatRepository envioRepository;
    @Autowired private ImpresionService impresionService;

    private Long serieGuia;

    @BeforeEach
    void prepararGuias() {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", null,
                    "Jr. Prueba 1", "150115", null, null, null));
        }
        serieGuia = serie(TipoComprobante.GUIA_REMISION_REMITENTE, "T991");
    }

    @Test
    void trasladoEntreLocalesMueveElStockYLoEnlazaConLaGuia() {
        GuiaRemisionResponse guia = guiaService.emitir(traslado(List.of(new ItemCantidadRequest(jeanB, 4))));

        assertThat(guia.numero()).isEqualTo("T991-00000001");
        assertThat(guia.motivo()).isEqualTo(MotivoTraslado.TRASLADO_ESTABLECIMIENTOS);
        assertThat(guia.destinatario().numeroDocumento()).isEqualTo("20000000001");
        assertThat(guia.partida().codigoLocal()).isEqualTo("9101");
        assertThat(guia.llegada().codigoLocal()).isEqualTo("9102");
        assertThat(guia.detalles()).singleElement().satisfies(d -> {
            assertThat(d.cantidad()).isEqualTo(4);
            assertThat(d.descripcion()).startsWith("Jean prueba - ");
        });
        assertThat(stock(jeanB, tienda)).isEqualTo(6);
        assertThat(stock(jeanB, almacen)).isEqualTo(4);
        var kardex = inventarioService.kardex(jeanB, almacen, null, null, PageRequest.of(0, 5)).content();
        assertThat(kardex.getFirst().documento()).isEqualTo("Guía de remisión T991-00000001");
        assertThat(guia.sunat().estado()).isEqualTo(EstadoSunat.PENDIENTE);
    }

    @Test
    void sinStockSuficienteNoSeEmiteNiSeConsumeNumero() {
        assertThatThrownBy(() -> guiaService.emitir(traslado(List.of(new ItemCantidadRequest(jeanA, 50)))))
                .isInstanceOf(BusinessException.class);
        assertThat(guiaService.emitir(traslado(List.of(new ItemCantidadRequest(jeanA, 1)))).numero()).isEqualTo("T991-00000001");
    }

    @Test
    void ventaDespachaLoPendienteYNuncaMasDeLoVendido() {
        VentaResponse factura = factura(3);

        GuiaRemisionResponse parcial = guiaService.emitir(venta(factura.id(), List.of(new ItemCantidadRequest(jeanA, 1))));
        assertThat(parcial.comprobante()).isEqualTo(factura.numero());
        assertThat(parcial.destinatario().nombre()).isEqualTo("EMPRESA PRUEBA SAC");
        assertThat(stock(jeanA, tienda)).as("la venta ya movió el stock").isEqualTo(2);

        assertThatThrownBy(() -> guiaService.emitir(venta(factura.id(), List.of(new ItemCantidadRequest(jeanA, 3)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("solo quedan 2 por despachar");

        GuiaRemisionResponse resto = guiaService.emitir(venta(factura.id(), null));
        assertThat(resto.detalles()).singleElement().satisfies(d -> assertThat(d.cantidad()).isEqualTo(2));
        assertThatThrownBy(() -> guiaService.emitir(venta(factura.id(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya tiene guía de remisión");
    }

    @Test
    void laGuiaDeUnaVentaExigeIdentificarAlDestinatario() {
        VentaResponse boleta = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        assertThatThrownBy(() -> guiaService.emitir(venta(boleta.id(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Clientes varios");
    }

    @Test
    void transportePublicoExigeUnRucValidoYPrivadoElConductor() {
        VentaResponse factura = factura(1);
        GuiaRemisionRequest base = venta(factura.id(), null);
        GuiaRemisionRequest rucInvalido = new GuiaRemisionRequest(base.serieId(), base.motivo(), null, base.comprobanteId(),
                base.llegada(), null, BigDecimal.ONE, null, ModalidadTransporte.PUBLICO,
                new GuiaRemisionRequest.Transportista("20123456789", "TRANSPORTES X", null), false, null, null, null, null);
        assertThatThrownBy(() -> guiaService.emitir(rucInvalido))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transportista: El RUC");

        GuiaRemisionRequest sinConductor = new GuiaRemisionRequest(base.serieId(), base.motivo(), null, base.comprobanteId(),
                base.llegada(), null, BigDecimal.ONE, null, ModalidadTransporte.PRIVADO, null, false, null, null, null, null);
        assertThatThrownBy(() -> guiaService.emitir(sinConductor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("indica el conductor");

        GuiaRemisionRequest conConductor = new GuiaRemisionRequest(base.serieId(), base.motivo(), null, base.comprobanteId(),
                base.llegada(), null, BigDecimal.ONE, null, ModalidadTransporte.PRIVADO, null, false,
                new GuiaRemisionRequest.Conductor(TipoDocumentoIdentidad.DNI, "45678912", "Juan", "Pérez Rojas", "Q45678912"),
                "ABC123", null, null);
        GuiaRemisionResponse guia = guiaService.emitir(conConductor);
        assertThat(guia.conductor().licencia()).isEqualTo("Q45678912");
        assertThat(guia.placa()).isEqualTo("ABC123");
    }

    @Test
    void elTrasladoNoPuedeEmpezarAyer() {
        GuiaRemisionRequest r = traslado(List.of(new ItemCantidadRequest(jeanA, 1)));
        GuiaRemisionRequest ayer = new GuiaRemisionRequest(r.serieId(), r.motivo(), LocalDate.now().minusDays(1), null, null,
                r.establecimientoDestinoId(), r.pesoBrutoKg(), null, r.modalidad(), null, true, null, null, null, r.items());
        assertThatThrownBy(() -> guiaService.emitir(ayer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no puede ser anterior a hoy");
    }

    @Test
    void sinAccesoALaApiDeGuiasQuedaPendienteYExplicaPorQue() {
        GuiaRemisionResponse guia = guiaService.emitir(traslado(List.of(new ItemCantidadRequest(jeanA, 1))));
        Long envioId = envioRepository.findByGuiaId(guia.id()).orElseThrow().getId();

        var envio = envioSunatService.enviarEnvioAhora(envioId, false);

        assertThat(envio.estado()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(envio.intentos()).as("no gasta intentos").isZero();
        assertThat(envio.tieneXml()).isTrue();
        assertThat(envio.descripcionRespuesta()).contains("Falta configurar el acceso a la API de guías");
    }

    @Test
    void imprimeLaGuiaConSusDatosDeTraslado() throws Exception {
        GuiaRemisionResponse guia = guiaService.emitir(traslado(List.of(new ItemCantidadRequest(jeanB, 2))));

        byte[] pdf = impresionService.generarGuia(guia.id()).contenido();

        String texto = new org.openpdf.text.pdf.parser.PdfTextExtractor(new org.openpdf.text.pdf.PdfReader(pdf)).getTextFromPage(1);
        assertThat(texto).contains("GUÍA DE REMISIÓN REMITENTE", "T991-00000001",
                "04 - Traslado entre establecimientos", "PUNTO DE LLEGADA", "establecimiento 9102", "Total de unidades");
    }

    // ---------- Datos ----------

    /** De la tienda al almacén, en auto propio (M1). */
    private GuiaRemisionRequest traslado(List<ItemCantidadRequest> items) {
        return new GuiaRemisionRequest(serieGuia, MotivoTraslado.TRASLADO_ESTABLECIMIENTOS, null, null, null, almacen,
                new BigDecimal("5.2"), 1, ModalidadTransporte.PRIVADO, null, true, null, null, null, items);
    }

    /** Entrega de una venta por transporte público. */
    private GuiaRemisionRequest venta(Long comprobanteId, List<ItemCantidadRequest> items) {
        return new GuiaRemisionRequest(serieGuia, MotivoTraslado.VENTA, null, comprobanteId,
                new GuiaRemisionRequest.Direccion("150101", "Av. Abancay 100, Lima"), null, new BigDecimal("2.0"), 1,
                ModalidadTransporte.PUBLICO, new GuiaRemisionRequest.Transportista("20100066603", "TRANSPORTES DEMO SAC", null),
                false, null, null, null, items);
    }

    private VentaResponse factura(int cantidad) {
        return vender(TipoComprobante.FACTURA, serieFactura, clienteRuc, List.of(item(jeanA, cantidad, null)),
                List.of(efectivo(new BigDecimal("89.90").multiply(BigDecimal.valueOf(cantidad)).toPlainString(), null)));
    }
}
