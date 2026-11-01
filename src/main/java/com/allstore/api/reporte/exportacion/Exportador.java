package com.allstore.api.reporte.exportacion;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Exporta un reporte a Excel (.xlsx) o CSV. El Excel trae encabezado fijo, filtros y formatos de
 * moneda y fecha; el CSV es UTF-8 con BOM (Excel lo abre con tildes) y punto decimal, listo para
 * pandas: {@code pd.read_csv("archivo.csv")}.
 *
 * <p>El Excel se escribe por partes (SXSSF): un año de ventas no se carga entero en memoria.
 */
@Component
public class Exportador {

    private static final DateTimeFormatter FECHA_HORA_CSV = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] xlsx(Reporte<?> reporte) {
        return escribirXlsx(reporte);
    }

    public byte[] csv(Reporte<?> reporte) {
        return escribirCsv(reporte);
    }

    private static <T> byte[] escribirXlsx(Reporte<T> reporte) {
        try (SXSSFWorkbook libro = new SXSSFWorkbook(200); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(reporte.titulo().length() > 31 ? reporte.titulo().substring(0, 31) : reporte.titulo());
            Map<Columna.Tipo, CellStyle> estilos = estilos(libro);
            CellStyle encabezado = encabezado(libro);
            List<Columna<T>> columnas = reporte.columnas();

            Row titulos = hoja.createRow(0);
            for (int i = 0; i < columnas.size(); i++) {
                Cell celda = titulos.createCell(i);
                celda.setCellValue(columnas.get(i).titulo());
                celda.setCellStyle(encabezado);
                hoja.setColumnWidth(i, ancho(columnas.get(i)));
            }
            int numero = 1;
            for (T fila : reporte.filas()) {
                Row row = hoja.createRow(numero++);
                for (int i = 0; i < columnas.size(); i++) {
                    escribir(row.createCell(i), columnas.get(i), columnas.get(i).valor().apply(fila), estilos);
                }
            }
            hoja.createFreezePane(0, 1);
            if (!columnas.isEmpty()) {
                hoja.setAutoFilter(new CellRangeAddress(0, Math.max(0, numero - 1), 0, columnas.size() - 1));
            }
            libro.write(salida);
            libro.dispose();
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }

    private static void escribir(Cell celda, Columna<?> columna, Object valor, Map<Columna.Tipo, CellStyle> estilos) {
        if (valor == null) {
            return;
        }
        switch (columna.tipo()) {
            case ENTERO, MONEDA, PORCENTAJE -> celda.setCellValue(((Number) valor).doubleValue());
            case FECHA -> celda.setCellValue((LocalDate) valor);
            case FECHA_HORA -> celda.setCellValue(valor instanceof OffsetDateTime o ? o.toLocalDateTime() : (LocalDateTime) valor);
            case SI_NO -> celda.setCellValue(Boolean.TRUE.equals(valor) ? "Sí" : "No");
            case TEXTO -> celda.setCellValue(String.valueOf(valor));
        }
        CellStyle estilo = estilos.get(columna.tipo());
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    private static Map<Columna.Tipo, CellStyle> estilos(SXSSFWorkbook libro) {
        Map<Columna.Tipo, CellStyle> estilos = new EnumMap<>(Columna.Tipo.class);
        estilos.put(Columna.Tipo.ENTERO, formato(libro, "#,##0"));
        estilos.put(Columna.Tipo.MONEDA, formato(libro, "#,##0.00"));
        estilos.put(Columna.Tipo.PORCENTAJE, formato(libro, "0.0"));
        estilos.put(Columna.Tipo.FECHA, formato(libro, "dd/mm/yyyy"));
        estilos.put(Columna.Tipo.FECHA_HORA, formato(libro, "dd/mm/yyyy hh:mm"));
        return estilos;
    }

    private static CellStyle formato(SXSSFWorkbook libro, String patron) {
        CellStyle estilo = libro.createCellStyle();
        estilo.setDataFormat(libro.createDataFormat().getFormat(patron));
        return estilo;
    }

    private static CellStyle encabezado(SXSSFWorkbook libro) {
        CellStyle estilo = libro.createCellStyle();
        Font negrita = libro.createFont();
        negrita.setBold(true);
        negrita.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(negrita);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        return estilo;
    }

    /** Ancho en 1/256 de carácter: el del título o el típico de su tipo, lo que sea mayor. */
    private static int ancho(Columna<?> columna) {
        int tipico = switch (columna.tipo()) {
            case TEXTO -> 22;
            case FECHA -> 11;
            case FECHA_HORA -> 16;
            case MONEDA -> 13;
            default -> 9;
        };
        return Math.min(60, Math.max(tipico, columna.titulo().length() + 3)) * 256;
    }

    private static <T> byte[] escribirCsv(Reporte<T> reporte) {
        StringBuilder sb = new StringBuilder("﻿");
        List<Columna<T>> columnas = reporte.columnas();
        sb.append(String.join(",", columnas.stream().map(c -> campo(c.titulo())).toList())).append("\r\n");
        for (T fila : reporte.filas()) {
            for (int i = 0; i < columnas.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(campo(textoCsv(columnas.get(i), columnas.get(i).valor().apply(fila))));
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String textoCsv(Columna<?> columna, Object valor) {
        if (valor == null) {
            return "";
        }
        return switch (columna.tipo()) {
            case MONEDA -> valor instanceof BigDecimal b ? b.setScale(2, RoundingMode.HALF_UP).toPlainString() : valor.toString();
            case FECHA_HORA -> valor instanceof OffsetDateTime o ? FECHA_HORA_CSV.format(o) : FECHA_HORA_CSV.format((LocalDateTime) valor);
            case SI_NO -> Boolean.TRUE.equals(valor) ? "1" : "0";
            default -> valor.toString();
        };
    }

    /**
     * Entre comillas si lleva coma, comillas o salto de línea. Un texto que empieza con = + - @ se
     * precede de un apóstrofo: así Excel no lo ejecuta como fórmula (inyección CSV con datos de clientes).
     */
    static String campo(String texto) {
        String t = texto;
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0 && !esNumero(t)) {
            t = "'" + t;
        }
        if (t.contains(",") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            t = "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }

    private static boolean esNumero(String t) {
        return t.matches("-?\\d+(\\.\\d+)?");
    }
}
