package com.allstore.api.contabilidad.service;

import com.allstore.api.contabilidad.dto.RegistroVentasResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Archivo plano para reemplazar la propuesta del RVIE en el SIRE, según el Anexo 3 de la
 * RS 112-2021/SUNAT (modificada por la RS 040-2022) y la estructura que publica SUNAT ("Estructura
 * del Reemplazo RVIE", verificada campo por campo el 01/11/2026):
 * <ul>
 *   <li>Campos 1 a 33 separados por "|"; los campos 34 a 40 los completa SUNAT y los 41 a 57
 *       (libres) no se usan.</li>
 *   <li>Fechas DD/MM/AAAA; importes con 2 decimales, sin separador de miles; negativos (-#.##)
 *       en las notas de crédito.</li>
 *   <li>Campo 4 (CAR) vacío: lo asigna SUNAT.</li>
 * </ul>
 * Nombre: LE + RUC + AAAA + MM + 00 + 140400 (RVIE) + 02 (reemplaza la propuesta) + O (1 = empresa
 * operativa) + I (1 con información, 0 sin ella) + M (1 = soles) + 2 (fijo).
 */
public final class ArchivoRvie {

    static final String SEPARADOR = "|";
    private static final String FIN_DE_LINEA = "\r\n";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ArchivoRvie() {
    }

    public record Archivo(String nombre, byte[] contenido) {
    }

    /** El .txt del reemplazo (vacío si no hubo operaciones: así se declara un periodo sin ventas). */
    public static Archivo txt(RegistroVentasResponse registro) {
        String contenido = registro.registros().stream()
                .map(r -> linea(registro, r))
                .collect(Collectors.joining(FIN_DE_LINEA, "", registro.registros().isEmpty() ? "" : FIN_DE_LINEA));
        return new Archivo(nombre(registro) + ".txt", contenido.getBytes(StandardCharsets.UTF_8));
    }

    /** El mismo archivo comprimido: el SIRE recibe el reemplazo en .zip. */
    public static Archivo zip(RegistroVentasResponse registro) {
        Archivo txt = txt(registro);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry(txt.nombre()));
            zip.write(txt.contenido());
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Archivo(nombre(registro) + ".zip", salida.toByteArray());
    }

    static String nombre(RegistroVentasResponse registro) {
        String conInformacion = registro.registros().isEmpty() ? "0" : "1";
        return "LE" + registro.ruc() + registro.periodo() + "00" + "140400" + "02" + "1" + conInformacion + "1" + "2";
    }

    /** Campos 1 a 33 del Anexo 3, en orden. */
    static String linea(RegistroVentasResponse registro, RegistroVentasResponse.Registro r) {
        RegistroVentasResponse.DocumentoModificado m = r.modifica();
        BigDecimal cero = BigDecimal.ZERO;
        return String.join(SEPARADOR, List.of(
                registro.ruc(),                                   // 1  RUC
                texto(registro.razonSocial()),                    // 2  Apellidos y nombres o razón social
                registro.periodo(),                               // 3  Periodo AAAAMM
                "",                                               // 4  CAR SUNAT: lo asigna SUNAT
                fecha(r.fechaEmision()),                          // 5  Fecha de emisión
                "",                                               // 6  Fecha de vencimiento (solo tipo 14)
                r.tipoComprobante(),                              // 7  Tipo de comprobante (Catálogo 01)
                r.serie(),                                        // 8  Serie
                String.valueOf(r.numero()),                       // 9  Número
                "",                                               // 10 Número final (solo resúmenes)
                vacioSiNulo(r.clienteTipoDocumento()),            // 11 Tipo de documento del cliente
                vacioSiNulo(r.clienteNumeroDocumento()),          // 12 Número de documento del cliente
                texto(r.clienteNombre()),                         // 13 Nombre o razón social del cliente
                importe(cero),                                    // 14 Valor facturado de exportación
                importe(r.baseGravada()),                         // 15 Base imponible gravada
                importe(cero),                                    // 16 Descuento de la base (global)
                importe(r.igv()),                                 // 17 IGV / IPM
                importe(cero),                                    // 18 Descuento del IGV (global)
                importe(r.exonerado()),                           // 19 Exonerado
                importe(r.inafecto()),                            // 20 Inafecto
                importe(cero),                                    // 21 ISC
                importe(cero),                                    // 22 Base gravada IVAP
                importe(cero),                                    // 23 IVAP
                importe(cero),                                    // 24 ICBPER
                importe(cero),                                    // 25 Otros tributos
                importe(r.total()),                               // 26 Total del comprobante
                r.moneda(),                                       // 27 Moneda
                "",                                               // 28 Tipo de cambio (solo si no es PEN)
                m == null ? "" : fecha(m.fechaEmision()),         // 29 Fecha del comprobante modificado
                m == null ? "" : m.tipoComprobante(),             // 30 Tipo del comprobante modificado
                m == null ? "" : m.serie(),                       // 31 Serie del comprobante modificado
                m == null ? "" : String.valueOf(m.numero()),      // 32 Número del comprobante modificado
                ""                                                // 33 ID proyecto de operadores
        ));
    }

    static String importe(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fecha(LocalDate fecha) {
        return FECHA.format(fecha);
    }

    /** Sin separadores ni saltos de línea, que romperían el archivo. */
    private static String texto(String valor) {
        return valor == null ? "" : valor.replace(SEPARADOR, " ").replaceAll("\\s+", " ").trim();
    }

    private static String vacioSiNulo(String valor) {
        return valor == null ? "" : valor;
    }
}
