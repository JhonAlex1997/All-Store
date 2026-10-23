package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.dto.ProductoDetalleResponse;
import com.allstore.api.producto.dto.VarianteUpdateRequest;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaItemRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Precio por mayor: desde el mínimo de prendas de la empresa (3 por defecto, de cualquier modelo)
 * o porque el cajero lo marca aunque sea 1 prenda.
 */
class PrecioPorMayorIntegrationTest extends TiendaDePrueba {

    @Autowired private EmpresaService empresaService;

    /** Short de S/ 59.90, por mayor S/ 42.00, con dos variantes y 20 unidades de cada una. */
    private Long shortA;
    private Long shortB;

    @BeforeEach
    void productoConPrecioPorMayor() {
        ProductoDetalleResponse corto = productoService.crear(new ProductoCreateRequest("PRUEBA-MAY", "Short prueba", null,
                1L, new BigDecimal("59.90"), new BigDecimal("42.00"), List.of(3L, 4L), List.of(1L)));
        shortA = corto.variantes().get(0).id();
        shortB = corto.variantes().get(1).id();
        inventarioService.registrar(tienda, MotivoMovimiento.INVENTARIO_INICIAL,
                List.of(new InventarioService.Linea(shortA, 20), new InventarioService.Linea(shortB, 20)), null);
    }

    @Test
    void desdeTresPrendasDeCualquierTallaVaAPrecioPorMayor() {
        VentaResponse venta = vender(List.of(item(shortA, 2, null), item(shortB, 1, null)), "126.00", null);

        assertThat(venta.porMayor()).isTrue();
        assertThat(venta.porMayorManual()).isFalse();
        assertThat(venta.detalles()).allSatisfy(d -> assertThat(d.precioUnitario()).isEqualByComparingTo("42.00"));
        assertThat(venta.total()).isEqualByComparingTo("126.00");
    }

    @Test
    void conDosPrendasSeCobraElPrecioNormal() {
        VentaResponse venta = vender(List.of(item(shortA, 2, null)), "119.80", null);

        assertThat(venta.porMayor()).isFalse();
        assertThat(venta.detalles().getFirst().precioUnitario()).isEqualByComparingTo("59.90");
    }

    @Test
    void conElCheckUnaSolaPrendaVaAPrecioPorMayorYQuedaComoManual() {
        VentaResponse venta = vender(List.of(item(shortA, 1, null)), "42.00", true);

        assertThat(venta.porMayor()).isTrue();
        assertThat(venta.porMayorManual()).isTrue();
        assertThat(venta.total()).isEqualByComparingTo("42.00");
    }

    @Test
    void unModeloSinPrecioPorMayorSeCobraNormalAunqueSeanTres() {
        // El jean de la tienda de prueba no tiene precio por mayor; el short sí.
        VentaResponse venta = vender(List.of(item(jeanA, 2, null), item(shortA, 1, null)), "221.80", null);

        assertThat(venta.porMayor()).isTrue();
        assertThat(venta.detalles()).extracting(VentaResponse.Detalle::precioUnitario)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("89.90"), new BigDecimal("42.00"));
    }

    @Test
    void elMinimoDePrendasSeConfiguraEnLaEmpresa() {
        empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", null,
                "Jr. Prueba 1", "150115", null, null, 6));

        VentaResponse tres = vender(List.of(item(shortA, 3, null)), "179.70", null);
        assertThat(tres.porMayor()).isFalse();
        VentaResponse seis = vender(List.of(item(shortA, 6, null)), "252.00", null);
        assertThat(seis.porMayor()).isTrue();
    }

    @Test
    void unaVarianteConPrecioPorMayorPropioLoUsa() {
        productoService.actualizarVariante(shortB, new VarianteUpdateRequest(null, new BigDecimal("40.00"), true));

        VentaResponse venta = vender(List.of(item(shortB, 3, null)), "120.00", null);

        assertThat(venta.detalles().getFirst().precioUnitario()).isEqualByComparingTo("40.00");
    }

    @Test
    void elPrecioPorMayorNoPuedeSuperarAlNormal() {
        assertThatThrownBy(() -> productoService.crear(new ProductoCreateRequest("PRUEBA-MAL", "Mal", null, 1L,
                new BigDecimal("50.00"), new BigDecimal("55.00"), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no puede ser mayor que el precio normal");
    }

    @Test
    void alConvertirLaNotaDeVentaConservaElPrecioPorMayor() {
        VentaResponse nota = ventaService.crear(new VentaRequest(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, null,
                List.of(item(shortA, 3, null)), List.of(efectivo("126.00", null)), null));

        VentaResponse boleta = ventaService.convertir(nota.id(), new ConvertirVentaRequest(TipoComprobante.BOLETA, serieBoleta, null));

        assertThat(boleta.porMayor()).isTrue();
        assertThat(boleta.total()).isEqualByComparingTo("126.00");
    }

    private VentaResponse vender(List<VentaItemRequest> items, String total, Boolean porMayor) {
        return ventaService.crear(new VentaRequest(TipoComprobante.BOLETA, serieBoleta, null, null, items,
                List.of(new PagoRequest(com.allstore.api.venta.entity.MetodoPago.EFECTIVO, new BigDecimal(total), null, null)),
                porMayor));
    }
}
