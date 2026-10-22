package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.dto.ConceptoNotaDebitoRequest;
import com.allstore.api.venta.dto.NotaDebitoRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaDebito;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Notas de débito sobre boletas y facturas (ver TiendaDePrueba para los datos de partida). */
class NotaDebitoIntegrationTest extends TiendaDePrueba {

    @Test
    void cobraUnConceptoGravadoSinMoverStockYEntraALaCaja() {
        VentaResponse factura = factura();
        int stockAntes = stock(jeanA, tienda);

        VentaResponse nota = notaDebitoService.emitir(factura.id(), new NotaDebitoRequest(serieNdFactura,
                MotivoNotaDebito.PENALIDADES, "Penalidad por pago fuera de plazo",
                List.of(new ConceptoNotaDebitoRequest("Penalidad", new BigDecimal("23.60"), null)),
                List.of(new PagoRequest(MetodoPago.YAPE, new BigDecimal("23.60"), null, "123456"))));

        assertThat(nota.tipoComprobante()).isEqualTo(TipoComprobante.NOTA_DEBITO);
        assertThat(nota.numero()).isEqualTo("FD91-00000001");
        assertThat(nota.comprobanteOrigen()).isEqualTo(factura.numero());
        assertThat(nota.motivoNotaDebito()).isEqualTo(MotivoNotaDebito.PENALIDADES);
        assertThat(nota.cliente().numeroDocumento()).isEqualTo("20123456786");
        assertThat(nota.opGravadas()).isEqualByComparingTo("20.00");
        assertThat(nota.totalIgv()).isEqualByComparingTo("3.60");
        assertThat(nota.total()).isEqualByComparingTo("23.60");
        assertThat(nota.detalles().getFirst().varianteId()).isNull();
        assertThat(nota.detalles().getFirst().unidadMedida()).isEqualTo("ZZ");
        assertThat(stock(jeanA, tienda)).isEqualTo(stockAntes);
        // La caja cobró la factura (89.90) y la nota de débito (23.60).
        assertThat(cajaService.miCaja().resumen().totalVentas()).isEqualByComparingTo("113.50");
        assertThat(ventaService.obtenerDetalle(factura.id()).notasDebito()).containsExactly("FD91-00000001");
    }

    @Test
    void unConceptoInafectoNoLlevaIgv() {
        VentaResponse nota = notaDebitoService.emitir(boleta().id(), new NotaDebitoRequest(serieNdBoleta,
                MotivoNotaDebito.INTERESES_MORA, "Intereses moratorios",
                List.of(new ConceptoNotaDebitoRequest("Intereses por mora", new BigDecimal("5.00"), "30")),
                List.of(efectivo("5.00", null))));

        assertThat(nota.opInafectas()).isEqualByComparingTo("5.00");
        assertThat(nota.opGravadas()).isEqualByComparingTo("0.00");
        assertThat(nota.totalIgv()).isEqualByComparingTo("0.00");
    }

    @Test
    void laSerieDebeEmpezarConLaLetraDelComprobante() {
        VentaResponse boleta = boleta();
        assertThatThrownBy(() -> notaDebitoService.emitir(boleta.id(), new NotaDebitoRequest(serieNdFactura,
                MotivoNotaDebito.AUMENTO_VALOR, "Ajuste", List.of(concepto("10.00")), List.of(efectivo("10.00", null)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("que empiece con B");
    }

    @Test
    void losPagosDebenSumarElTotalDeLaNota() {
        VentaResponse boleta = boleta();
        assertThatThrownBy(() -> notaDebitoService.emitir(boleta.id(), new NotaDebitoRequest(serieNdBoleta,
                MotivoNotaDebito.AUMENTO_VALOR, "Ajuste", List.of(concepto("10.00")), List.of(efectivo("5.00", null)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pero el total es S/ 10.00");
    }

    @Test
    void unaNotaDeVentaNoAdmiteNotaDeDebito() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
        assertThatThrownBy(() -> notaDebitoService.emitir(nota.id(), new NotaDebitoRequest(serieNdBoleta,
                MotivoNotaDebito.AUMENTO_VALOR, "Ajuste", List.of(concepto("10.00")), List.of(efectivo("10.00", null)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("convierte primero la nota de venta");
    }

    private VentaResponse boleta() {
        return vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
    }

    private VentaResponse factura() {
        return vender(TipoComprobante.FACTURA, serieFactura, clienteRuc, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
    }

    private static ConceptoNotaDebitoRequest concepto(String monto) {
        return new ConceptoNotaDebitoRequest("Aumento en el valor", new BigDecimal(monto), null);
    }
}
