package com.allstore.api.venta;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.compra.dto.AnularCompraRequest;
import com.allstore.api.compra.dto.CompraRequest;
import com.allstore.api.compra.dto.CompraResponse;
import com.allstore.api.compra.dto.ProveedorRequest;
import com.allstore.api.compra.entity.TipoDocumentoCompra;
import com.allstore.api.compra.service.CompraService;
import com.allstore.api.compra.service.ProveedorService;
import com.allstore.api.insumo.dto.InsumoRequest;
import com.allstore.api.insumo.dto.StockInsumoResponse;
import com.allstore.api.insumo.service.InsumoService;
import com.allstore.api.producto.entity.ProductoVariante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Compras: entran prendas e insumos al stock, se separa el IGV (solo facturas) y se pueden anular. */
class CompraIntegrationTest extends TiendaDePrueba {

    @Autowired private CompraService compraService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private InsumoService insumoService;

    private Long proveedor;
    private Long tela;

    @BeforeEach
    void datos() {
        proveedor = proveedorService.crear(new ProveedorRequest(TipoDocumentoIdentidad.RUC, "20100047218", "TEXTIL PRUEBA SAC",
                "Av. Textil 1", null, null, null)).id();
        tela = insumoService.crear(new InsumoRequest("DENIM-14OZ", "Denim 14 oz azul", "MTR", null, null)).id();
    }

    @Test
    void unaFacturaHaceEntrarPrendasEInsumosYSeparaElIgv() {
        CompraResponse compra = compraService.registrar(new CompraRequest(proveedor, tienda, TipoDocumentoCompra.FACTURA,
                "F001", "123", LocalDate.now().minusDays(2), null, null, true, List.of(
                new CompraRequest.Item(jeanA, null, null, new BigDecimal("10"), new BigDecimal("40.00"), null),
                new CompraRequest.Item(null, tela, null, new BigDecimal("25.5"), new BigDecimal("12.00"), null),
                new CompraRequest.Item(null, null, "Flete", new BigDecimal("1"), new BigDecimal("50.00"), false))));

        assertThat(compra.baseGravada()).isEqualByComparingTo("706.00");   // 400 + 306
        assertThat(compra.igv()).isEqualByComparingTo("127.08");
        assertThat(compra.noGravado()).isEqualByComparingTo("50.00");
        assertThat(compra.total()).isEqualByComparingTo("883.08");
        assertThat(stock(jeanA, tienda)).isEqualTo(15);
        assertThat(insumoService.stock(tienda)).filteredOn(s -> s.insumoId().equals(tela))
                .extracting(StockInsumoResponse::cantidad).singleElement()
                .satisfies(c -> assertThat(c).isEqualByComparingTo("25.5"));
        // actualizarCostos = true: el costo de la prenda pasa a ser el de la compra.
        assertThat(productoService.obtenerVariantes(List.of(jeanA)).get(jeanA).getCostoEfectivo()).isEqualByComparingTo("40.00");

        assertThatThrownBy(() -> compraService.registrar(new CompraRequest(proveedor, tienda, TipoDocumentoCompra.FACTURA,
                "f001", "123", LocalDate.now(), null, null, false, List.of(
                new CompraRequest.Item(jeanA, null, null, BigDecimal.ONE, BigDecimal.TEN, null)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya está registrada");
    }

    @Test
    void unaBoletaNoTieneCreditoFiscalYSinPedirloNoCambiaElCosto() {
        CompraResponse compra = compraService.registrar(new CompraRequest(proveedor, tienda, TipoDocumentoCompra.BOLETA,
                "B001", "9", LocalDate.now(), null, null, false, List.of(
                new CompraRequest.Item(jeanB, null, null, new BigDecimal("2"), new BigDecimal("50.00"), null))));
        assertThat(compra.igv()).isEqualByComparingTo("0");
        assertThat(compra.noGravado()).isEqualByComparingTo("100.00");
        ProductoVariante variante = productoService.obtenerVariantes(List.of(jeanB)).get(jeanB);
        assertThat(variante.getCostoEfectivo()).isNull();
    }

    @Test
    void anularSacaDelStockLoQueEntro() {
        CompraResponse compra = compraService.registrar(new CompraRequest(proveedor, tienda, TipoDocumentoCompra.FACTURA,
                "F001", "200", LocalDate.now(), null, null, false, List.of(
                new CompraRequest.Item(jeanA, null, null, new BigDecimal("3"), new BigDecimal("40.00"), null))));
        assertThat(stock(jeanA, tienda)).isEqualTo(8);

        CompraResponse anulada = compraService.anular(compra.id(), new AnularCompraRequest("Mercadería devuelta"));
        assertThat(anulada.estado()).isEqualTo("ANULADA");
        assertThat(stock(jeanA, tienda)).isEqualTo(5);
        assertThatThrownBy(() -> compraService.registrar(new CompraRequest(proveedor, tienda, TipoDocumentoCompra.FACTURA,
                "F001", "300", LocalDate.now(), null, null, false, List.of(
                new CompraRequest.Item(jeanA, null, null, new BigDecimal("1.5"), BigDecimal.TEN, null)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("número entero");
    }
}
