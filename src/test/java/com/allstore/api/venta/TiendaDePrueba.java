package com.allstore.api.venta;

import com.allstore.api.caja.dto.AbrirCajaRequest;
import com.allstore.api.caja.service.CajaService;
import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.dto.SerieCreateRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.entity.TipoEstablecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.inventario.dto.StockEstablecimientoResponse;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.dto.ProductoDetalleResponse;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaItemRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.service.NotaCreditoService;
import com.allstore.api.venta.service.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tienda de prueba para las pruebas de ventas, notas de crédito y caja contra allstore_test:
 * dos locales, series de todos los tipos, un jean con dos variantes y stock, dos clientes y la
 * caja abierta (con S/ 100 de fondo). Todo se revierte al terminar cada prueba.
 */
@SpringBootTest
@Transactional
abstract class TiendaDePrueba {

    @Autowired protected VentaService ventaService;
    @Autowired protected NotaCreditoService notaCreditoService;
    @Autowired protected InventarioService inventarioService;
    @Autowired protected CajaService cajaService;
    @Autowired protected ProductoService productoService;
    @Autowired protected ClienteService clienteService;
    @Autowired protected EstablecimientoService establecimientoService;
    @Autowired protected SerieService serieService;

    protected Long tienda;
    protected Long almacen;
    protected Long serieNotaVenta;
    protected Long serieBoleta;
    protected Long serieFactura;
    protected Long serieNcBoleta;
    protected Long serieNcFactura;
    /** Jean de S/ 89.90 con 5 unidades en la tienda. */
    protected Long jeanA;
    /** Jean de S/ 89.90 con 10 unidades en la tienda. */
    protected Long jeanB;
    protected Long clienteDni;
    protected Long clienteRuc;

    @BeforeEach
    void armarTienda() {
        tienda = establecimientoService.crear(new EstablecimientoRequest("9101", "Tienda prueba",
                TipoEstablecimiento.TIENDA, "Jr. Prueba 1", "150115", true)).id();
        almacen = establecimientoService.crear(new EstablecimientoRequest("9102", "Almacén prueba",
                TipoEstablecimiento.ALMACEN, "Jr. Prueba 2", "150115", true)).id();
        serieNotaVenta = serie(TipoComprobante.NOTA_VENTA, "NV91");
        serieBoleta = serie(TipoComprobante.BOLETA, "B991");
        serieFactura = serie(TipoComprobante.FACTURA, "F991");
        serieNcBoleta = serie(TipoComprobante.NOTA_CREDITO, "BC91");
        serieNcFactura = serie(TipoComprobante.NOTA_CREDITO, "FC91");

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

        cajaService.abrir(new AbrirCajaRequest(tienda, new BigDecimal("100.00")));
    }

    protected Long serie(TipoComprobante tipo, String codigo) {
        return serieService.crear(new SerieCreateRequest(tipo, codigo, tienda, 0L)).id();
    }

    protected VentaResponse vender(TipoComprobante tipo, Long serieId, Long clienteId, List<VentaItemRequest> items,
                                   List<PagoRequest> pagos) {
        return ventaService.crear(new VentaRequest(tipo, serieId, clienteId, null, items, pagos));
    }

    protected static VentaItemRequest item(Long varianteId, int cantidad, String descuento) {
        return new VentaItemRequest(varianteId, cantidad, descuento == null ? null : new BigDecimal(descuento));
    }

    protected static PagoRequest efectivo(String monto, String recibido) {
        return new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal(monto),
                recibido == null ? null : new BigDecimal(recibido), null);
    }

    protected int stock(Long varianteId, Long establecimientoId) {
        return inventarioService.stockPorVariante(varianteId).stream()
                .filter(s -> s.establecimientoId().equals(establecimientoId))
                .mapToInt(StockEstablecimientoResponse::cantidad)
                .findFirst().orElse(0);
    }
}
