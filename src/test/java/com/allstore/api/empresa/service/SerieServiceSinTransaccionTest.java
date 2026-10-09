package com.allstore.api.empresa.service;

import com.allstore.api.empresa.entity.TipoComprobante;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sin @Transactional a propósito, y sin preparar datos para no dejar nada guardado en la base. */
@SpringBootTest
class SerieServiceSinTransaccionTest {

    @Autowired
    private SerieService serieService;

    @Test
    void asignarNumeroExigeEstarDentroDeUnaTransaccion() {
        // Sin la transacción de una venta, un número tomado no se revertiría si la venta falla.
        assertThatThrownBy(() -> serieService.asignarSiguienteNumero(1L, TipoComprobante.BOLETA))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
