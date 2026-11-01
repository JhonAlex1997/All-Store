package com.allstore.api.venta;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.compra.dto.CompraRequest;
import com.allstore.api.compra.dto.ProveedorRequest;
import com.allstore.api.compra.entity.TipoDocumentoCompra;
import com.allstore.api.compra.service.CompraService;
import com.allstore.api.compra.service.ProveedorService;
import com.allstore.api.contabilidad.dto.AsientoResponse;
import com.allstore.api.contabilidad.dto.LibroDiarioResponse;
import com.allstore.api.contabilidad.service.AsientoService;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Asientos: cada documento genera un asiento que cuadra, con las cuentas del PCGE que corresponden. */
class AsientoIntegrationTest extends TiendaDePrueba {

    @Autowired private AsientoService asientoService;
    @Autowired private CompraService compraService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private EntityManager entityManager;

    @Test
    void ventaDevolucionCompraYCostoDeVentas() {
        productoService.definirCosto(jdbcModelo(), new BigDecimal("40.00"));
        // Boleta de 2 jeans: 89.90 en efectivo y 89.90 por Yape.
        VentaResponse boleta = vender(TipoComprobante.BOLETA, serieBoleta, clienteDni, List.of(item(jeanA, 2, null)),
                List.of(efectivo("89.90", null), new PagoRequest(MetodoPago.YAPE, new BigDecimal("89.90"), null, "op-1")));
        notaCreditoService.emitir(boleta.id(), new NotaCreditoRequest(serieNcBoleta, MotivoNotaCredito.DEVOLUCION_POR_ITEM,
                "Cambio", MetodoPago.EFECTIVO, null, List.of(new ItemDevolucionRequest(jeanA, 1))));
        Long proveedor = proveedorService.crear(new ProveedorRequest(TipoDocumentoIdentidad.RUC, "20100047218", "TEXTIL PRUEBA SAC",
                null, null, null, null)).id();
        compraService.registrar(new CompraRequest(proveedor, tienda, TipoDocumentoCompra.FACTURA, "F777", "1",
                LocalDate.now(ZoneId.of("America/Lima")), null, null, false,
                List.of(new CompraRequest.Item(jeanB, null, null, new BigDecimal("5"), new BigDecimal("40.00"), null))));
        entityManager.flush();

        LibroDiarioResponse diario = asientoService.libroDiario(YearMonth.now(ZoneId.of("America/Lima")));
        assertThat(diario.asientos()).allSatisfy(a -> assertThat(a.debe()).isEqualByComparingTo(a.haber()));

        AsientoResponse venta = asiento(diario, "Boleta " + boleta.numero());
        assertThat(venta.lineas()).extracting(AsientoResponse.Linea::cuenta).containsExactly("1011", "1041", "70111", "40111");
        assertThat(venta.lineas().get(2).haber()).isEqualByComparingTo("152.37");   // base de 179.80
        assertThat(venta.lineas().get(3).haber()).isEqualByComparingTo("27.43");

        AsientoResponse compra = asiento(diario, "Compra F777-1");
        assertThat(compra.lineas()).extracting(AsientoResponse.Linea::cuenta).containsExactly("60111", "40111", "4212");
        assertThat(compra.haber()).isEqualByComparingTo("236.00");
        assertThat(diario.asientos()).anySatisfy(a -> {
            assertThat(a.origen()).isEqualTo("DESTINO_COMPRA");
            assertThat(a.documento()).isEqualTo("Compra F777-1");
        });
        // Costo de ventas de hoy: incluye 2 vendidas - 1 devuelta a S/ 40 (otras pruebas pueden sumar más del día).
        assertThat(diario.asientos()).anySatisfy(a -> assertThat(a.origen()).isEqualTo("COSTO_VENTA"));
    }

    private AsientoResponse asiento(LibroDiarioResponse diario, String documento) {
        return diario.asientos().stream().filter(a -> a.documento().equals(documento)).findFirst().orElseThrow();
    }

    private Long jdbcModelo() {
        return productoService.obtenerVariantes(List.of(jeanA)).get(jeanA).getProducto().getId();
    }
}
