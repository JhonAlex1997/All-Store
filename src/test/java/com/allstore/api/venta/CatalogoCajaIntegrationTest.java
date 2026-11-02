package com.allstore.api.venta;

import com.allstore.api.caja.dto.ProductoCajaResponse;
import com.allstore.api.caja.service.CatalogoCajaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Búsqueda de la caja: prendas del modelo con su stock en el local y sin cargar todo el catálogo. */
class CatalogoCajaIntegrationTest extends TiendaDePrueba {

    @Autowired private CatalogoCajaService catalogo;

    @Test
    void buscaPorNombreYTraeTallasPreciosYStockDelLocal() {
        List<ProductoCajaResponse> resultado = catalogo.buscar("jean prueba", tienda);

        assertThat(resultado).hasSize(1);
        ProductoCajaResponse jean = resultado.getFirst();
        assertThat(jean.codigo()).isEqualTo("PRUEBA-VTA");
        assertThat(jean.variantes()).extracting(ProductoCajaResponse.Variante::varianteId).containsExactly(jeanA, jeanB);
        assertThat(jean.variantes()).extracting(ProductoCajaResponse.Variante::stock).containsExactly(5, 10);
        assertThat(jean.variantes().getFirst().precio()).isEqualByComparingTo("89.90");
        assertThat(jean.variantes().getFirst().precioCampana()).isNull();
    }

    @Test
    void elStockEsDelLocalPedido() {
        // En el almacén no se registró inventario.
        assertThat(catalogo.buscar("PRUEBA-VTA", almacen).getFirst().variantes())
                .extracting(ProductoCajaResponse.Variante::stock).containsOnly(0);
    }

    @Test
    void encuentraPorCodigoDeBarrasYIgnoraBusquedasDeUnaLetra() {
        String barras = catalogo.buscar("jean prueba", tienda).getFirst().variantes().get(1).codigoBarras();

        List<ProductoCajaResponse> porBarras = catalogo.buscar(barras, tienda);
        assertThat(porBarras).hasSize(1);
        assertThat(porBarras.getFirst().variantes()).extracting(ProductoCajaResponse.Variante::varianteId)
                .containsExactly(jeanB);
        assertThat(catalogo.buscar("j", tienda)).isEmpty();
        assertThat(catalogo.buscar("no-existe-xyz", tienda)).isEmpty();
    }
}
