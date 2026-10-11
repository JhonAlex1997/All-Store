package com.allstore.api.venta;

import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.dto.SerieCreateRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.entity.TipoEstablecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.inventario.dto.MovimientoResponse;
import com.allstore.api.inventario.dto.StockEstablecimientoResponse;
import com.allstore.api.inventario.dto.TrasladoRequest;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.dto.ProductoDetalleResponse;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaItemRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.service.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de venta e inventario contra allstore_test. Cada prueba arma su propia tienda (local,
 * series, producto, stock) y todo se revierte al terminar.
 */
@SpringBootTest
@Transactional
class VentaServiceIntegrationTest {

    @Autowired private VentaService ventaService;
    @Autowired private InventarioService inventarioService;
    @Autowired private ProductoService productoService;
    @Autowired private ClienteService clienteService;
    @Autowired private EstablecimientoService establecimientoService;
    @Autowired private SerieService serieService;

    private Long tienda;
    private Long almacen;
    private Long serieNotaVenta;
    private Long serieBoleta;
    private Long serieFactura;
    private Long jeanA;   // 89.90
    private Long jeanB;   // 89.90
    private Long clienteDni;
    private Long clienteRuc;

    @BeforeEach
    void armarTienda() {
        tienda = establecimientoService.crear(new EstablecimientoRequest("9101", "Tienda prueba",
                TipoEstablecimiento.TIENDA, "Jr. Prueba 1", "150115", true)).id();
        almacen = establecimientoService.crear(new EstablecimientoRequest("9102", "Almacén prueba",
                TipoEstablecimiento.ALMACEN, "Jr. Prueba 2", "150115", true)).id();
        serieNotaVenta = serie(TipoComprobante.NOTA_VENTA, "NV91");
        serieBoleta = serie(TipoComprobante.BOLETA, "B991");
        serieFactura = serie(TipoComprobante.FACTURA, "F991");

        ProductoDetalleResponse jean = productoService.crear(new ProductoCreateRequest("PRUEBA-VTA", "Jean prueba", null,
                1L, new BigDecimal("89.90"), List.of(3L, 4L), List.of(1L)));
        jeanA = jean.variantes().get(0).id();
        jeanB = jean.variantes().get(1).id();
        inventarioService.registrar(tienda, MotivoMovimiento.INVENTARIO_INICIAL,
                List.of(new InventarioService.Linea(jeanA, 5), new InventarioService.Linea(jeanB, 10)), null);

        clienteDni = clienteService.crear(new ClienteRequest(TipoDocumentoIdentidad.DNI, "41234567", "Ana Prueba",
                null, null, null, null, true)).id();
        clienteRuc = clienteService.crear(new ClienteRequest(TipoDocumentoIdentidad.RUC, "20123456786",
                "EMPRESA PRUEBA SAC", "Av. Prueba 100", null, null, null, true)).id();
    }

    @Test
    void boletaCalculaIgvDescuentaStockYNumera() {
        VentaResponse venta = vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", "200.00")));

        assertThat(venta.numero()).isEqualTo("B991-00000001");
        assertThat(venta.total()).isEqualByComparingTo("179.80");
        assertThat(venta.opGravadas()).isEqualByComparingTo("152.37");
        assertThat(venta.totalIgv()).isEqualByComparingTo("27.43");
        assertThat(venta.vuelto()).isEqualByComparingTo("20.20");
        assertThat(venta.detalles().getFirst().descripcion()).contains("Jean prueba");
        assertThat(stock(jeanA, tienda)).isEqualTo(3);

        MovimientoResponse kardex = inventarioService.kardex(jeanA, tienda, null, null, PageRequest.of(0, 1))
                .content().getFirst();
        assertThat(kardex.motivo()).isEqualTo(MotivoMovimiento.VENTA);
        assertThat(kardex.saldo()).isEqualTo(3);
        assertThat(kardex.documento()).isEqualTo("Boleta de venta B991-00000001");
    }

    @Test
    void aceptaDescuentoYPagoMixto() {
        VentaResponse venta = vender(TipoComprobante.BOLETA, serieBoleta, clienteDni,
                List.of(item(jeanA, 1, "9.90")),
                List.of(new PagoRequest(MetodoPago.YAPE, new BigDecimal("50.00"), null, "OP-123"),
                        efectivo("30.00", null)));

        assertThat(venta.total()).isEqualByComparingTo("80.00");
        assertThat(venta.totalDescuento()).isEqualByComparingTo("9.90");
        assertThat(venta.pagos()).hasSize(2);
    }

    @Test
    void sinStockNoSeVendeNiSeConsumeNumero() {
        assertThatThrownBy(() -> vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 6, null)), List.of(efectivo("539.40", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuficiente");

        assertThat(serieService.listar(TipoComprobante.BOLETA, tienda).getFirst().siguienteNumero())
                .isEqualTo("B991-00000001");
    }

    @Test
    void lospagosDebenSumarElTotal() {
        assertThatThrownBy(() -> vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("80.00", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Los pagos suman S/ 80.00 pero el total de la venta es S/ 89.90");
    }

    @Test
    void facturaSoloAClienteConRuc() {
        assertThatThrownBy(() -> vender(TipoComprobante.FACTURA, serieFactura, clienteDni,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requiere un cliente con RUC");

        VentaResponse factura = vender(TipoComprobante.FACTURA, serieFactura, clienteRuc,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
        assertThat(factura.cliente().tipoDocumento()).isEqualTo("6");
    }

    @Test
    void boletaMayorA700ExigeIdentificarAlCliente() {
        // 8 x 89.90 = 719.20
        assertThatThrownBy(() -> vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanB, 8, null)), List.of(efectivo("719.20", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("identifica al cliente");

        VentaResponse conDni = vender(TipoComprobante.BOLETA, serieBoleta, clienteDni,
                List.of(item(jeanB, 8, null)), List.of(efectivo("719.20", null)));
        assertThat(conDni.cliente().numeroDocumento()).isEqualTo("41234567");
    }

    @Test
    void anularNotaDeVentaDevuelveElStock() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null)));
        assertThat(stock(jeanA, tienda)).isEqualTo(3);

        VentaResponse anulada = ventaService.anular(nota.id(), new AnularVentaRequest("Cliente se arrepintió"));
        assertThat(anulada.estado()).isEqualTo(EstadoComprobante.ANULADO);
        assertThat(stock(jeanA, tienda)).isEqualTo(5);

        assertThatThrownBy(() -> ventaService.anular(nota.id(), new AnularVentaRequest("otra vez")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya está anulado");
    }

    @Test
    void boletaNoSeAnulaDirectamente() {
        VentaResponse boleta = vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
        assertThatThrownBy(() -> ventaService.anular(boleta.id(), new AnularVentaRequest("error")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nota de crédito");
    }

    @Test
    void notaDeVentaSeConvierteEnFacturaSinMoverStock() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null)));

        VentaResponse factura = ventaService.convertir(nota.id(),
                new ConvertirVentaRequest(TipoComprobante.FACTURA, serieFactura, clienteRuc));

        assertThat(factura.numero()).isEqualTo("F991-00000001");
        assertThat(factura.comprobanteOrigen()).isEqualTo(nota.numero());
        assertThat(factura.total()).isEqualByComparingTo(nota.total());
        assertThat(stock(jeanA, tienda)).isEqualTo(3);

        VentaResponse notaDespues = ventaService.obtenerDetalle(nota.id());
        assertThat(notaDespues.estado()).isEqualTo(EstadoComprobante.CONVERTIDO);
        assertThat(notaDespues.convertidoEn()).isEqualTo("F991-00000001");
        assertThatThrownBy(() -> ventaService.anular(nota.id(), new AnularVentaRequest("x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya se convirtió");
    }

    @Test
    void trasladoMueveStockEntreLocales() {
        inventarioService.trasladar(new TrasladoRequest(tienda, almacen, "Reposición",
                List.of(new ItemCantidadRequest(jeanB, 4))));
        assertThat(stock(jeanB, tienda)).isEqualTo(6);
        assertThat(stock(jeanB, almacen)).isEqualTo(4);

        assertThatThrownBy(() -> inventarioService.trasladar(new TrasladoRequest(almacen, tienda, null,
                List.of(new ItemCantidadRequest(jeanB, 5)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuficiente");
    }

    // ---------- Ayudas ----------

    private Long serie(TipoComprobante tipo, String codigo) {
        return serieService.crear(new SerieCreateRequest(tipo, codigo, tienda, 0L)).id();
    }

    private VentaResponse vender(TipoComprobante tipo, Long serieId, Long clienteId, List<VentaItemRequest> items,
                                 List<PagoRequest> pagos) {
        return ventaService.crear(new VentaRequest(tipo, serieId, clienteId, null, items, pagos));
    }

    private static VentaItemRequest item(Long varianteId, int cantidad, String descuento) {
        return new VentaItemRequest(varianteId, cantidad, descuento == null ? null : new BigDecimal(descuento));
    }

    private static PagoRequest efectivo(String monto, String recibido) {
        return new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal(monto),
                recibido == null ? null : new BigDecimal(recibido), null);
    }

    private int stock(Long varianteId, Long establecimientoId) {
        return inventarioService.stockPorVariante(varianteId).stream()
                .filter(s -> s.establecimientoId().equals(establecimientoId))
                .mapToInt(StockEstablecimientoResponse::cantidad)
                .findFirst().orElse(0);
    }
}
