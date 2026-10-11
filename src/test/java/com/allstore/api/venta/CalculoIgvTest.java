package com.allstore.api.venta;

import com.allstore.api.venta.service.CalculoIgv;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalculoIgvTest {

    @Test
    void separaBaseEIgvDeUnPrecioConIgv() {
        CalculoIgv.Linea l = CalculoIgv.calcular(1, new BigDecimal("89.90"), BigDecimal.ZERO, "10");
        assertThat(l.total()).isEqualByComparingTo("89.90");
        assertThat(l.valorVenta()).isEqualByComparingTo("76.19");
        assertThat(l.igv()).isEqualByComparingTo("13.71");
    }

    @Test
    void aplicaCantidadYDescuentoAntesDeSepararElIgv() {
        // 3 x 79.90 = 239.70 - 10.00 = 229.70 -> base 194.66, IGV 35.04
        CalculoIgv.Linea l = CalculoIgv.calcular(3, new BigDecimal("79.90"), new BigDecimal("10.00"), "10");
        assertThat(l.total()).isEqualByComparingTo("229.70");
        assertThat(l.valorVenta()).isEqualByComparingTo("194.66");
        assertThat(l.igv()).isEqualByComparingTo("35.04");
    }

    @Test
    void baseMasIgvSiempreEsElTotalExacto() {
        for (int centimos = 1; centimos <= 50_000; centimos += 7) {
            BigDecimal precio = BigDecimal.valueOf(centimos, 2);
            CalculoIgv.Linea l = CalculoIgv.calcular(1, precio, BigDecimal.ZERO, "10");
            assertThat(l.valorVenta().add(l.igv())).isEqualByComparingTo(l.total());
        }
    }

    @Test
    void operacionExoneradaNoLlevaIgv() {
        CalculoIgv.Linea l = CalculoIgv.calcular(2, new BigDecimal("50.00"), BigDecimal.ZERO, "20");
        assertThat(l.valorVenta()).isEqualByComparingTo("100.00");
        assertThat(l.igv()).isEqualByComparingTo("0.00");
    }
}
