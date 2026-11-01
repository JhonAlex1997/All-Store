package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.inventario.dto.CerrarConteoRequest;
import com.allstore.api.inventario.dto.ConteoCantidadesRequest;
import com.allstore.api.inventario.dto.ConteoRequest;
import com.allstore.api.inventario.dto.ConteoResponse;
import com.allstore.api.inventario.entity.EstadoConteo;
import com.allstore.api.inventario.service.ConteoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Conteo físico: lo vendido después de contar no es faltante; al cerrar se ajusta el stock. */
class ConteoIntegrationTest extends TiendaDePrueba {

    @Autowired private ConteoService conteoService;

    @Test
    void lasDiferenciasSeAjustanAlCerrarYLoVendidoDespuesNoEsFaltante() {
        ConteoResponse conteo = conteoService.abrir(new ConteoRequest(tienda, null, null, "Conteo de fin de mes"));
        assertThat(conteo.prendas()).isEqualTo(2);
        assertThatThrownBy(() -> conteoService.abrir(new ConteoRequest(tienda, null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ya hay un conteo abierto");

        // jeanA: sistema 5, se cuentan 4 (falta 1). jeanB: sistema 10, se cuentan 11 (sobra 1).
        conteoService.registrar(conteo.id(), new ConteoCantidadesRequest(null, List.of(
                new ConteoCantidadesRequest.Item(jeanA, null, 4), new ConteoCantidadesRequest.Item(jeanB, null, 11))));
        // Después de contar se venden 2 jeanA: no deben aparecer como faltantes.
        vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null)));

        ConteoResponse cerrado = conteoService.cerrar(conteo.id(), new CerrarConteoRequest(false));
        assertThat(cerrado.estado()).isEqualTo(EstadoConteo.CERRADO);
        assertThat(cerrado.sobrantes()).isEqualTo(1);
        assertThat(cerrado.faltantes()).isEqualTo(1);
        assertThat(stock(jeanA, tienda)).isEqualTo(2); // 5 - 2 vendidas - 1 faltante
        assertThat(stock(jeanB, tienda)).isEqualTo(11);

        // Cerrar otra vez no ajusta de nuevo.
        conteoService.cerrar(conteo.id(), new CerrarConteoRequest(false));
        assertThat(stock(jeanB, tienda)).isEqualTo(11);
    }

    @Test
    void escaneandoSeSumaYLoNoContadoPuedeIrACero() {
        ConteoResponse conteo = conteoService.abrir(new ConteoRequest(tienda, null, null, null));
        String sku = conteo.lineas().stream().filter(l -> l.varianteId().equals(jeanA)).findFirst().orElseThrow().sku();
        for (int i = 0; i < 3; i++) {
            conteoService.registrar(conteo.id(), new ConteoCantidadesRequest(ConteoCantidadesRequest.Modo.SUMAR,
                    List.of(new ConteoCantidadesRequest.Item(null, sku, 1))));
        }
        conteoService.cerrar(conteo.id(), new CerrarConteoRequest(true));
        assertThat(stock(jeanA, tienda)).isEqualTo(3);
        assertThat(stock(jeanB, tienda)).isZero();
    }
}
