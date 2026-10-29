package com.allstore.api.venta;

import com.allstore.api.inventario.dto.CargaMasivaResponse;
import com.allstore.api.inventario.service.CargaMasivaService;
import com.allstore.api.producto.entity.Color;
import com.allstore.api.producto.entity.Talla;
import com.allstore.api.producto.repository.ColorRepository;
import com.allstore.api.producto.repository.ProductoRepository;
import com.allstore.api.producto.repository.ProductoVarianteRepository;
import com.allstore.api.producto.repository.TallaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Carga masiva del catálogo e inventario inicial desde Excel (todo o nada). */
class CargaMasivaIntegrationTest extends TiendaDePrueba {

    @Autowired private CargaMasivaService cargaMasivaService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoVarianteRepository varianteRepository;
    @Autowired private TallaRepository tallaRepository;
    @Autowired private ColorRepository colorRepository;

    private String talla1;
    private String talla2;
    private String color;

    @BeforeEach
    void catalogo() {
        talla1 = tallaRepository.findById(3L).map(Talla::getCodigo).orElseThrow();
        talla2 = tallaRepository.findById(4L).map(Talla::getCodigo).orElseThrow();
        color = colorRepository.findById(1L).map(Color::getNombre).orElseThrow();
    }

    @Test
    void primeroRevisaSinGuardarYLuegoCargaModelosVariantesYStock() throws Exception {
        String categoria = categoriaDeLaTienda();
        byte[] excel = excel(
                new String[]{"Código", "Nombre", "Categoría", "Precio", "Precio por mayor", "Talla", "Color", "Stock"},
                new String[]{"cm-01", "Jean Carga", categoria.toLowerCase(), "94.90", "68", talla1, color.toUpperCase(), "6"},
                new String[]{"CM-01", "Jean Carga", categoria, "94.90", "68", talla2, color, "4"});

        CargaMasivaResponse revision = cargar(excel, false);
        assertThat(revision.errores()).isEmpty();
        assertThat(revision.aplicada()).isFalse();
        assertThat(revision.modelosNuevos()).isEqualTo(1);
        assertThat(revision.variantesNuevas()).isEqualTo(2);
        assertThat(revision.unidades()).isEqualTo(10);
        assertThat(productoRepository.existsByCodigoIgnoreCase("CM-01")).isFalse();

        CargaMasivaResponse carga = cargar(excel, true);
        assertThat(carga.aplicada()).isTrue();
        Long variante = varianteRepository.findByCodigoBarrasOrSku(sku(talla2)).orElseThrow().getId();
        assertThat(stock(variante, tienda)).isEqualTo(4);
        assertThat(productoRepository.findByCodigoIgnoreCase("CM-01").orElseThrow().getPrecioPorMayor())
                .isEqualByComparingTo("68.00");

        // Cargar el mismo archivo otra vez no duplica nada: el stock de lo que ya existe va por ajuste.
        CargaMasivaResponse repetida = cargar(excel, true);
        assertThat(repetida.aplicada()).isFalse();
        assertThat(repetida.errores()).hasSize(2).allSatisfy(e -> assertThat(e.mensaje()).contains("ajuste de inventario"));
        assertThat(stock(variante, tienda)).isEqualTo(4);
    }

    @Test
    void conUnErrorNoGuardaNadaYDiceQueCorregirEnCadaFila() throws Exception {
        String categoria = categoriaDeLaTienda();
        byte[] excel = excel(
                new String[]{"codigo", "nombre", "categoria", "precio", "talla", "color", "stock"},
                new String[]{"CM-02", "Jean Bueno", categoria, "80", talla1, color, "3"},
                new String[]{"CM-02", "Jean Bueno", categoria, "80", talla1, color, "3"},
                new String[]{"CM-03", "Jean Malo", categoria, "-5", talla1, "Fucsia neón", "dos"},
                new String[]{"CM-04", "Visita http://estafa.pe", categoria, "50", talla1, color, "1"});

        CargaMasivaResponse resultado = cargar(excel, true);

        assertThat(resultado.aplicada()).isFalse();
        assertThat(resultado.errores()).extracting(CargaMasivaResponse.ErrorFila::fila).containsExactly(3, 4, 5);
        assertThat(resultado.errores().get(0).mensaje()).contains("repetido");
        assertThat(resultado.errores().get(1).mensaje()).contains("no puede ser negativo", "'Fucsia neón' no existe", "entero");
        assertThat(resultado.errores().get(2).mensaje()).contains("No se permiten enlaces");
        assertThat(productoRepository.existsByCodigoIgnoreCase("CM-02")).isFalse();
    }

    @Test
    void faltanColumnasObligatorias() throws Exception {
        CargaMasivaResponse resultado = cargar(excel(new String[]{"codigo", "nombre"}, new String[]{"X", "Y"}), false);
        assertThat(resultado.errores()).singleElement().satisfies(e -> assertThat(e.mensaje())
                .contains("Faltan las columnas: categoria, precio, talla, color"));
    }

    @Test
    void laPlantillaTraeElCatalogoQueReconoceElSistema() throws Exception {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(cargaMasivaService.plantilla()))) {
            assertThat(libro.getSheet("Productos").getRow(0).getCell(0).getStringCellValue()).isEqualTo("codigo");
            assertThat(libro.getSheet("Catálogo").getRow(0).getCell(2).getStringCellValue()).isEqualTo("color");
        }
    }

    // ---------- Ayudas ----------

    private CargaMasivaResponse cargar(byte[] excel, boolean aplicar) {
        return cargaMasivaService.cargar(new ByteArrayInputStream(excel), tienda, aplicar);
    }

    private String categoriaDeLaTienda() {
        return productoRepository.findByCodigoIgnoreCase("PRUEBA-VTA").orElseThrow().getCategoria().getNombre();
    }

    private String sku(String talla) {
        Color c = colorRepository.findById(1L).orElseThrow();
        return ("CM-01-" + c.getCodigo() + "-" + talla).toUpperCase();
    }

    private static byte[] excel(String[]... filas) throws Exception {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Productos");
            for (int i = 0; i < filas.length; i++) {
                Row fila = hoja.createRow(i);
                for (int j = 0; j < filas[i].length; j++) {
                    fila.createCell(j).setCellValue(filas[i][j]);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
