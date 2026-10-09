package com.allstore.api.producto.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoVarianteTest {

    @Test
    void skuUneModeloColorYTallaEnMayusculas() {
        assertThat(CodigoVariante.sku("jn001", "azu", "32")).isEqualTo("JN001-AZU-32");
    }

    @Test
    void digitoDeControlCoincideConUnEanReal() {
        // 4006381333931 es un EAN-13 válido conocido.
        assertThat(CodigoVariante.digitoControlEan13("400638133393")).isEqualTo(1);
    }

    @Test
    void ean13InternoTienePrefijo2Y13Digitos() {
        String ean = CodigoVariante.ean13(15);
        assertThat(ean).hasSize(13).startsWith("200000000015");
        assertThat(ean.charAt(12) - '0').isEqualTo(CodigoVariante.digitoControlEan13(ean.substring(0, 12)));
    }

    @Test
    void idDemasiadoGrandeNoGeneraCodigo() {
        assertThatThrownBy(() -> CodigoVariante.ean13(100_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
