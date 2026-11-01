package com.allstore.api.contabilidad;

import com.allstore.api.contabilidad.dto.ValidacionSireResponse;
import com.allstore.api.contabilidad.dto.ValidacionSireResponse.Inconsistencia;
import com.allstore.api.contabilidad.service.ArchivoRvie;
import com.allstore.api.contabilidad.service.ValidadorSire;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** Las reglas de la estructura oficial del SIRE: lo correcto pasa y cada error se ubica por fila y campo. */
class ValidadorSireTest {

    private static final String RUC = "20000000001";
    private static final String NOMBRE_RCE = "LE" + RUC + "20250300080400021112.txt";
    private static final String NOMBRE_RVIE = "LE" + RUC + "20250300140400021112.txt";

    @Test
    void unArchivoDeComprasCorrectoPasa() {
        ValidacionSireResponse r = ValidadorSire.rce(archivo(NOMBRE_RCE, rce("10/03/2025", "01", "F001", "123", "6", "20100047218",
                "100.00", "18.00", "0.00", "118.00")));
        assertThat(r.inconsistencias()).isEmpty();
        assertThat(r.valido()).isTrue();
        assertThat(r.filas()).isEqualTo(1);
    }

    @Test
    void unArchivoDeVentasCorrectoPasaTambienConNotaNegativaYBoletaSinCliente() {
        String factura = rvie("05/03/2025", "01", "F001", "1", "6", "20100047218", "152.37", "27.43", "179.80", "", "");
        String boleta = rvie("06/03/2025", "03", "B001", "2", "", "", "76.19", "13.71", "89.90", "", "");
        String nota = rvie("07/03/2025", "07", "FC01", "1", "6", "20100047218", "-76.19", "-13.71", "-89.90", "01", "F001");
        assertThat(ValidadorSire.rvie(archivo(NOMBRE_RVIE, factura + "\r\n" + boleta + "\r\n" + nota)).inconsistencias()).isEmpty();
    }

    @Test
    void cadaErrorSeUbicaPorFilaYCampo() {
        String malRuc = rce("10/03/2025", "01", "F001", "1", "6", "20100047219", "100.00", "18.00", "0.00", "118.00");
        String malaFecha = rce("31/02/2025", "01", "F001", "2", "6", "20100047218", "100.00", "18.00", "0.00", "118.00");
        String noCuadra = rce("10/03/2025", "01", "F001", "3", "6", "20100047218", "100.00", "18.00", "0.00", "120.00");
        String igvMal = rce("10/03/2025", "01", "F001", "4", "6", "20100047218", "100.00", "25.00", "0.00", "125.00");
        String repetido = rce("10/03/2025", "01", "F001", "4", "6", "20100047218", "100.00", "18.00", "0.00", "118.00");
        ValidacionSireResponse r = ValidadorSire.rce(archivo(NOMBRE_RCE, String.join("\r\n", malRuc, malaFecha, noCuadra, igvMal, repetido)));

        assertThat(r.valido()).isFalse();
        assertThat(r.inconsistencias()).extracting(Inconsistencia::fila, Inconsistencia::campo)
                .contains(org.assertj.core.groups.Tuple.tuple(1, 13), org.assertj.core.groups.Tuple.tuple(2, 5),
                        org.assertj.core.groups.Tuple.tuple(3, 25), org.assertj.core.groups.Tuple.tuple(4, 16),
                        org.assertj.core.groups.Tuple.tuple(5, 0));
        assertThat(r.inconsistencias()).filteredOn(i -> i.fila() == 3).singleElement()
                .satisfies(i -> assertThat(i.mensaje()).contains("118.00"));
    }

    @Test
    void elNombreDebeCoincidirConElContenido() {
        String linea = rce("10/03/2025", "01", "F001", "1", "6", "20100047218", "100.00", "18.00", "0.00", "118.00");
        assertThat(ValidadorSire.rce(archivo("LE" + RUC + "20250300080400001112.txt", linea)).inconsistencias())
                .as("oportunidad 00 es de no domiciliados, pero el formato es válido; el periodo y el RUC sí se cruzan").isEmpty();
        assertThat(ValidadorSire.rce(archivo("LE" + RUC + "20250400080400021112.txt", linea)).inconsistencias())
                .anySatisfy(i -> assertThat(i.campo()).isEqualTo(3));
        assertThat(ValidadorSire.rce(archivo("LE" + RUC + "20250300080400021012.txt", linea)).inconsistencias())
                .anySatisfy(i -> assertThat(i.mensaje()).contains("no hay información (0)"));
        assertThat(ValidadorSire.rce(archivo("compras.txt", linea)).inconsistencias())
                .anySatisfy(i -> assertThat(i.nombreCampo()).isEqualTo("Nombre del archivo"));
        assertThat(ValidadorSire.rce(archivo(NOMBRE_RCE, "﻿" + linea)).inconsistencias())
                .anySatisfy(i -> assertThat(i.mensaje()).contains("BOM"));
    }

    @Test
    void lineasConCamposDeMenos() {
        assertThat(ValidadorSire.rce(archivo(NOMBRE_RCE, RUC + "|EMPRESA|202503")).inconsistencias())
                .singleElement().satisfies(i -> assertThat(i.mensaje()).contains("entre 41 y 80 campos"));
    }

    // ---------- Líneas de prueba ----------

    private static String rce(String fecha, String tipo, String serie, String numero, String tipoDoc, String doc, String base,
                              String igv, String noGravado, String total) {
        String[] c = new String[41];
        java.util.Arrays.fill(c, "");
        c[0] = RUC;
        c[1] = "EMPRESA PRUEBA SAC";
        c[2] = "202503";
        c[4] = fecha;
        c[6] = tipo;
        c[7] = serie;
        c[9] = numero;
        c[11] = tipoDoc;
        c[12] = doc;
        c[13] = "PROVEEDOR SAC";
        for (int i = 14; i <= 23; i++) {
            c[i] = "0.00";
        }
        c[14] = base;
        c[15] = igv;
        c[20] = noGravado;
        c[24] = total;
        c[25] = "PEN";
        c[32] = "1";
        return String.join("|", c);
    }

    private static String rvie(String fecha, String tipo, String serie, String numero, String tipoDoc, String doc, String base,
                               String igv, String total, String tipoModificado, String serieModificada) {
        String[] c = new String[33];
        java.util.Arrays.fill(c, "");
        c[0] = RUC;
        c[1] = "EMPRESA PRUEBA SAC";
        c[2] = "202503";
        c[4] = fecha;
        c[6] = tipo;
        c[7] = serie;
        c[8] = numero;
        c[10] = tipoDoc;
        c[11] = doc;
        c[12] = doc.isEmpty() ? "" : "CLIENTE SAC";
        for (int i = 13; i <= 24; i++) {
            c[i] = "0.00";
        }
        c[14] = base;
        c[16] = igv;
        c[25] = total;
        c[26] = "PEN";
        if (!tipoModificado.isEmpty()) {
            c[28] = "01/03/2025";
            c[29] = tipoModificado;
            c[30] = serieModificada;
            c[31] = "1";
        }
        return String.join("|", Collections.nCopies(1, String.join("|", c)));
    }

    private static ArchivoRvie.Archivo archivo(String nombre, String contenido) {
        return new ArchivoRvie.Archivo(nombre, contenido.getBytes(StandardCharsets.UTF_8));
    }
}
