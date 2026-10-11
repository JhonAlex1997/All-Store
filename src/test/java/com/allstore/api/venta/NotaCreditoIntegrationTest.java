package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Notas de crédito sobre boletas y facturas (ver TiendaDePrueba para los datos de partida). */
class NotaCreditoIntegrationTest extends TiendaDePrueba {

    @Test
    void devolucionPorItemReingresaStockYReembolsa() {
        VentaResponse boleta = boletaDeDos(null);
        assertThat(stock(jeanA, tienda)).isEqualTo(3);

        VentaResponse nota = devolver(boleta.id(), serieNcBoleta, 1);

        assertThat(nota.tipoComprobante()).isEqualTo(TipoComprobante.NOTA_CREDITO);
        assertThat(nota.numero()).isEqualTo("BC91-00000001");
        assertThat(nota.comprobanteOrigen()).isEqualTo(boleta.numero());
        assertThat(nota.motivoNota()).isEqualTo(MotivoNotaCredito.DEVOLUCION_POR_ITEM);
        assertThat(nota.total()).isEqualByComparingTo("89.90");
        assertThat(nota.pagos().getFirst().monto()).isEqualByComparingTo("89.90");
        assertThat(nota.cliente().nombre()).isEqualTo(boleta.cliente().nombre());
        assertThat(stock(jeanA, tienda)).isEqualTo(4);

        assertThat(ventaService.obtenerDetalle(boleta.id()).notasCredito()).containsExactly("BC91-00000001");
    }

    @Test
    void nuncaSeDevuelveMasDeLoVendido() {
        VentaResponse boleta = boletaDeDos(null);
        devolver(boleta.id(), serieNcBoleta, 1);

        assertThatThrownBy(() -> devolver(boleta.id(), serieNcBoleta, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("solo se pueden devolver 1");
    }

    @Test
    void elDescuentoSeProrrateaAlDevolverParte() {
        // 2 x 89.90 - 10.00 = 169.80; se devuelve 1: descuento 5.00, total 84.90
        VentaResponse boleta = boletaDeDos("10.00");
        VentaResponse nota = devolver(boleta.id(), serieNcBoleta, 1);

        assertThat(nota.totalDescuento()).isEqualByComparingTo("5.00");
        assertThat(nota.total()).isEqualByComparingTo("84.90");
    }

    @Test
    void devolucionTotalRevierteTodoYNoAdmiteOtraNota() {
        VentaResponse boleta = boletaDeDos("10.00");
        VentaResponse nota = notaCreditoService.emitir(boleta.id(), new NotaCreditoRequest(serieNcBoleta,
                MotivoNotaCredito.DEVOLUCION_TOTAL, "Producto con falla", MetodoPago.EFECTIVO, null, null));

        assertThat(nota.total()).isEqualByComparingTo(boleta.total());
        assertThat(stock(jeanA, tienda)).isEqualTo(5);
        assertThatThrownBy(() -> devolver(boleta.id(), serieNcBoleta, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("solo se pueden devolver 0");
    }

    @Test
    void devolucionTotalNoProcedeSiYaHayNotasParciales() {
        VentaResponse boleta = boletaDeDos(null);
        devolver(boleta.id(), serieNcBoleta, 1);

        assertThatThrownBy(() -> notaCreditoService.emitir(boleta.id(), new NotaCreditoRequest(serieNcBoleta,
                MotivoNotaCredito.ANULACION_OPERACION, "Error", MetodoPago.EFECTIVO, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya tiene notas de crédito");
    }

    @Test
    void laSerieDebeEmpezarConLaLetraDelComprobante() {
        VentaResponse boleta = boletaDeDos(null);

        assertThatThrownBy(() -> devolver(boleta.id(), serieNcFactura, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empiece con B");
    }

    @Test
    void notaDeVentaNoLlevaNotaDeCredito() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));

        assertThatThrownBy(() -> devolver(nota.id(), serieNcBoleta, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nota de venta no lleva nota de crédito");
    }

    private VentaResponse boletaDeDos(String descuento) {
        String total = descuento == null ? "179.80" : "169.80";
        return vender(TipoComprobante.BOLETA, serieBoleta, clienteDni,
                List.of(item(jeanA, 2, descuento)), List.of(efectivo(total, null)));
    }

    private VentaResponse devolver(Long comprobanteId, Long serieId, int cantidad) {
        return notaCreditoService.emitir(comprobanteId, new NotaCreditoRequest(serieId,
                MotivoNotaCredito.DEVOLUCION_POR_ITEM, "Cambio de talla", MetodoPago.EFECTIVO, null,
                List.of(new ItemDevolucionRequest(jeanA, cantidad))));
    }
}
