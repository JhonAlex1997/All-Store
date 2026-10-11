package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.inventario.dto.MovimientoResponse;
import com.allstore.api.inventario.dto.TrasladoRequest;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de venta e inventario (ver TiendaDePrueba para los datos de partida). */
class VentaServiceIntegrationTest extends TiendaDePrueba {

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
}
