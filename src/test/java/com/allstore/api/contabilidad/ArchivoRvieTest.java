package com.allstore.api.contabilidad;

import com.allstore.api.contabilidad.dto.RegistroVentasResponse;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.DocumentoModificado;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.Registro;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.Situacion;
import com.allstore.api.contabilidad.service.ArchivoRvie;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Archivo de reemplazo del RVIE según el Anexo 3 de la RS 112-2021/SUNAT, sin base de datos. */
class ArchivoRvieTest {

    @Test
    void nombreSegunSunat() {
        assertThat(ArchivoRvie.txt(registro(List.of(factura()))).nombre())
                .isEqualTo("LE2000000000120261000140400021112.txt");
        // Sin operaciones: indicador de contenido 0 y archivo vacío (así se declara un mes sin ventas).
        ArchivoRvie.Archivo vacio = ArchivoRvie.txt(registro(List.of()));
        assertThat(vacio.nombre()).isEqualTo("LE2000000000120261000140400021012.txt");
        assertThat(vacio.contenido()).isEmpty();
    }

    @Test
    void facturaConLosCamposDelAnexo3() {
        String[] campos = lineas(registro(List.of(factura())))[0].split("\\|", -1);

        assertThat(campos).hasSize(33);
        assertThat(campos[0]).isEqualTo("20000000001");
        assertThat(campos[1]).isEqualTo("EMPRESA DEMO S.A.C.");
        assertThat(campos[2]).isEqualTo("202610");
        assertThat(campos[3]).as("el CAR lo asigna SUNAT").isEmpty();
        assertThat(campos[4]).isEqualTo("22/10/2026");
        assertThat(campos[6]).isEqualTo("01");
        assertThat(campos[7]).isEqualTo("F001");
        assertThat(campos[8]).isEqualTo("15");
        assertThat(campos[10]).isEqualTo("6");
        assertThat(campos[11]).isEqualTo("20123456786");
        assertThat(campos[12]).as("sin el separador ni espacios de más").isEqualTo("TEXTIL DEMO S.A.C.");
        assertThat(campos[14]).isEqualTo("152.37");
        assertThat(campos[16]).isEqualTo("27.43");
        assertThat(campos[25]).isEqualTo("179.80");
        assertThat(campos[26]).isEqualTo("PEN");
        assertThat(campos[27]).as("tipo de cambio solo si no es PEN").isEmpty();
        assertThat(campos[28]).isEmpty();
    }

    @Test
    void notaDeCreditoEnNegativoConElComprobanteQueModifica() {
        Registro nota = new Registro(2L, LocalDate.of(2026, 10, 23), "07", "FC01", 3, "6", "20123456786", "TEXTIL DEMO",
                new BigDecimal("-76.19"), new BigDecimal("-13.71"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-89.90"),
                "PEN", new DocumentoModificado(LocalDate.of(2026, 10, 22), "01", "F001", 15), Situacion.ACEPTADO);

        String[] campos = lineas(registro(List.of(nota)))[0].split("\\|", -1);

        assertThat(campos[14]).isEqualTo("-76.19");
        assertThat(campos[16]).isEqualTo("-13.71");
        assertThat(campos[25]).isEqualTo("-89.90");
        assertThat(campos).endsWith("22/10/2026", "01", "F001", "15", "");
    }

    @Test
    void boletaSinIdentificarAlClienteVaSinDatosDelCliente() {
        Registro boleta = new Registro(3L, LocalDate.of(2026, 10, 22), "03", "B001", 7, null, null, null,
                new BigDecimal("76.19"), new BigDecimal("13.71"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("89.90"),
                "PEN", null, Situacion.ACEPTADO);

        String[] campos = lineas(registro(List.of(boleta)))[0].split("\\|", -1);

        assertThat(campos[10]).isEmpty();
        assertThat(campos[11]).isEmpty();
        assertThat(campos[12]).isEmpty();
    }

    @Test
    void elZipLlevaElTxtConElMismoNombre() throws Exception {
        ArchivoRvie.Archivo zip = ArchivoRvie.zip(registro(List.of(factura())));

        assertThat(zip.nombre()).isEqualTo("LE2000000000120261000140400021112.zip");
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip.contenido()))) {
            assertThat(entrada.getNextEntry().getName()).isEqualTo("LE2000000000120261000140400021112.txt");
            assertThat(new String(entrada.readAllBytes(), StandardCharsets.UTF_8)).startsWith("20000000001|EMPRESA DEMO S.A.C.|202610||");
        }
    }

    // ---------- Datos ----------

    private static Registro factura() {
        return new Registro(1L, LocalDate.of(2026, 10, 22), "01", "F001", 15, "6", "20123456786", "TEXTIL | DEMO S.A.C.",
                new BigDecimal("152.37"), new BigDecimal("27.43"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("179.80"),
                "PEN", null, Situacion.ACEPTADO);
    }

    private static RegistroVentasResponse registro(List<Registro> registros) {
        return new RegistroVentasResponse("202610", "20000000001", "EMPRESA DEMO S.A.C.", registros, List.of(), null, List.of());
    }

    private static String[] lineas(RegistroVentasResponse registro) {
        return new String(ArchivoRvie.txt(registro).contenido(), StandardCharsets.UTF_8).split("\r\n");
    }
}
