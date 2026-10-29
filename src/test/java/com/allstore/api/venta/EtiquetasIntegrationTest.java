package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.producto.dto.EtiquetasRequest;
import com.allstore.api.producto.dto.EtiquetasRequest.Formato;
import com.allstore.api.producto.dto.EtiquetasRequest.Item;
import com.allstore.api.producto.repository.ProductoVarianteRepository;
import com.allstore.api.producto.service.EtiquetaService;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Etiquetas con código de barras: una por página en rollo, 24 por hoja en A4. */
class EtiquetasIntegrationTest extends TiendaDePrueba {

    @Autowired private EtiquetaService etiquetaService;
    @Autowired private ProductoVarianteRepository varianteRepository;

    @Test
    void enRolloUnaEtiquetaPorPaginaConModeloPrecioYCodigo() throws Exception {
        byte[] pdf = etiquetaService.generar(new EtiquetasRequest(Formato.ROLLO_50X25,
                List.of(new Item(jeanA, 2), new Item(jeanB, 1))));

        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isEqualTo(3);
        String primera = new PdfTextExtractor(lector).getTextFromPage(1);
        String codigo = varianteRepository.findById(jeanA).orElseThrow().getCodigoBarras();
        assertThat(primera).contains("Jean prueba", "S/ 89.90", codigo);
        assertThat(codigo).hasSize(13).startsWith("2");
    }

    @Test
    void enA4SonVeinticuatroPorHoja() throws Exception {
        byte[] pdf = etiquetaService.generar(new EtiquetasRequest(Formato.A4, List.of(new Item(jeanA, 30))));

        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(2);
    }

    @Test
    void tieneUnMaximoPorImpresion() {
        assertThatThrownBy(() -> etiquetaService.generar(new EtiquetasRequest(Formato.A4,
                List.of(new Item(jeanA, 500), new Item(jeanB, 500), new Item(jeanA, 500), new Item(jeanB, 500), new Item(jeanA, 1)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("máximo");
    }
}
