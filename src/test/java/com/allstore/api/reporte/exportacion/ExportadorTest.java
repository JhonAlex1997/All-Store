package com.allstore.api.reporte.exportacion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportadorTest {

    record Fila(String nombre, BigDecimal monto) {
    }

    @Test
    void unTextoQueEmpiezaComoFormulaNoSeEjecutaEnExcel() {
        assertThat(Exportador.campo("=HIPERVINCULO(\"x\")")).isEqualTo("\"'=HIPERVINCULO(\"\"x\"\")\"");
        assertThat(Exportador.campo("+51 987")).isEqualTo("'+51 987");
        assertThat(Exportador.campo("@SUMA(A1)")).isEqualTo("'@SUMA(A1)");
        // Un número negativo sí se deja tal cual.
        assertThat(Exportador.campo("-89.90")).isEqualTo("-89.90");
    }

    @Test
    void elCsvEscapaComasYComillasYUsaPuntoDecimal() {
        Reporte<Fila> reporte = new Reporte<>("prueba", "Prueba", null, null,
                List.of(new Fila("Jean \"slim\", azul", new BigDecimal("89.9"))),
                List.of(Columna.texto("Nombre", Fila::nombre), Columna.moneda("Monto", Fila::monto)));
        String csv = new String(new Exportador().csv(reporte), StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("﻿Nombre,Monto\r\n\"Jean \"\"slim\"\", azul\",89.90\r\n");
    }
}
