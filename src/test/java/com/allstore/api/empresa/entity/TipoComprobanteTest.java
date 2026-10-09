package com.allstore.api.empresa.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TipoComprobanteTest {

    @Test
    void facturaYBoletaExigenSuLetraInicial() {
        assertThat(TipoComprobante.FACTURA.validarSerie("F001")).isEmpty();
        assertThat(TipoComprobante.FACTURA.validarSerie("B001")).isPresent();
        assertThat(TipoComprobante.BOLETA.validarSerie("B001")).isEmpty();
        assertThat(TipoComprobante.BOLETA.validarSerie("F001")).isPresent();
    }

    @Test
    void notasAceptanFoBSegunElComprobanteQueModifican() {
        assertThat(TipoComprobante.NOTA_CREDITO.validarSerie("FC01")).isEmpty();
        assertThat(TipoComprobante.NOTA_CREDITO.validarSerie("BC01")).isEmpty();
        assertThat(TipoComprobante.NOTA_DEBITO.validarSerie("FD01")).isEmpty();
        assertThat(TipoComprobante.NOTA_CREDITO.validarSerie("NC01")).isPresent();
    }

    @Test
    void guiaRemitenteEmpiezaConT() {
        assertThat(TipoComprobante.GUIA_REMISION_REMITENTE.validarSerie("T001")).isEmpty();
        assertThat(TipoComprobante.GUIA_REMISION_REMITENTE.validarSerie("G001")).isPresent();
    }

    @Test
    void notaDeVentaNoPuedeParecerUnComprobanteElectronico() {
        assertThat(TipoComprobante.NOTA_VENTA.validarSerie("NV01")).isEmpty();
        assertThat(TipoComprobante.NOTA_VENTA.validarSerie("B001")).isPresent();
        assertThat(TipoComprobante.NOTA_VENTA.isElectronico()).isFalse();
    }

    @Test
    void serieDebeTenerCuatroCaracteresEnMayuscula() {
        assertThat(TipoComprobante.BOLETA.validarSerie("B01")).isPresent();
        assertThat(TipoComprobante.BOLETA.validarSerie("b001")).isPresent();
        assertThat(TipoComprobante.BOLETA.validarSerie("B0001")).isPresent();
    }

    @Test
    void codigoDelCatalogo01SeConvierteEnTipo() {
        assertThat(TipoComprobante.desdeCodigo("07")).isEqualTo(TipoComprobante.NOTA_CREDITO);
        assertThat(TipoComprobante.desdeCodigo("NV")).isEqualTo(TipoComprobante.NOTA_VENTA);
    }
}
