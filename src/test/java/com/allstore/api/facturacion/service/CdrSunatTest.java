package com.allstore.api.facturacion.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Una constancia maliciosa (bomba zip) no agota la memoria. */
class CdrSunatTest {

    @Test
    void unaConstanciaGiganteSeRechazaSinLeerlaEntera() throws Exception {
        // 50 MB de ceros: comprimido pesa unos KB.
        byte[] bomba = zip(new byte[50 * 1024 * 1024]);
        assertThat(bomba.length).isLessThan(200 * 1024);

        assertThatThrownBy(() -> CdrSunat.descomprimir(bomba))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("supera");
    }

    @Test
    void unaConstanciaNormalSeLee() throws Exception {
        assertThat(CdrSunat.descomprimir(zip("<ApplicationResponse/>".getBytes()))).hasSize(22);
    }

    private static byte[] zip(byte[] contenido) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry("R-20000000001-01-F001-1.xml"));
            zip.write(contenido);
            zip.closeEntry();
        }
        return salida.toByteArray();
    }
}
