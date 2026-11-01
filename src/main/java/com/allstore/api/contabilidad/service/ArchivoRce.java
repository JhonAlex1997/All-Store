package com.allstore.api.contabilidad.service;

import com.allstore.api.contabilidad.dto.RegistroComprasResponse;

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
 * Archivo plano para reemplazar la propuesta del RCE en el SIRE (RS 112-2021/SUNAT, Anexo 11),
 * con el mismo formato que el del RVIE: campos separados por "|", fechas DD/MM/AAAA, importes con 2
 * decimales. Todas las compras con IGV se anotan como destinadas a operaciones gravadas (la tienda
 * solo vende prendas gravadas).
 *
 * <p>Nombre: LE + RUC + AAAA + MM + 00 + 080400 (RCE) + 02 (reemplazo) + 1 (operativa) + I (1 con
 * información, 0 sin ella) + 1 (soles) + 2. Antes del primer envío conviene pasarlo por el validador
 * PVSIRE de SUNAT.
 */
public final class ArchivoRce {

    static final String SEPARADOR = "|";
    private static final String FIN_DE_LINEA = "\r\n";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ArchivoRce() {
    }

    public static ArchivoRvie.Archivo txt(RegistroComprasResponse registro) {
        String contenido = registro.registros().stream()
                .map(r -> linea(registro, r))
                .collect(Collectors.joining(FIN_DE_LINEA, "", registro.registros().isEmpty() ? "" : FIN_DE_LINEA));
        return new ArchivoRvie.Archivo(nombre(registro) + ".txt", contenido.getBytes(StandardCharsets.UTF_8));
    }

    public static ArchivoRvie.Archivo zip(RegistroComprasResponse registro) {
        ArchivoRvie.Archivo txt = txt(registro);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry(txt.nombre()));
            zip.write(txt.contenido());
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ArchivoRvie.Archivo(nombre(registro) + ".zip", salida.toByteArray());
    }

    static String nombre(RegistroComprasResponse registro) {
        String conInformacion = registro.registros().isEmpty() ? "0" : "1";
        return "LE" + registro.ruc() + registro.periodo() + "00" + "080400" + "02" + "1" + conInformacion + "1" + "2";
    }

    static String linea(RegistroComprasResponse registro, RegistroComprasResponse.Registro r) {
        BigDecimal cero = BigDecimal.ZERO;
        return String.join(SEPARADOR, List.of(
                registro.ruc(),                         // 1  RUC
                texto(registro.razonSocial()),          // 2  Razón social
                registro.periodo(),                     // 3  Periodo AAAAMM
                "",                                     // 4  CAR SUNAT: lo asigna SUNAT
                fecha(r.fechaEmision()),                // 5  Fecha de emisión
                "",                                     // 6  Fecha de vencimiento o pago
                r.tipoComprobante(),                    // 7  Tipo de comprobante
                r.serie(),                              // 8  Serie
                "",                                     // 9  Año de la DUA o DSI
                r.numero(),                             // 10 Número
                "",                                     // 11 Número final
                r.proveedorTipoDocumento(),             // 12 Tipo de documento del proveedor
                r.proveedorNumeroDocumento(),           // 13 Número de documento del proveedor
                texto(r.proveedorNombre()),             // 14 Razón social del proveedor
                importe(r.baseGravada()),               // 15 Base imponible destinada a operaciones gravadas
                importe(r.igv()),                       // 16 IGV de esa base (crédito fiscal)
                importe(cero),                          // 17 Base destinada a operaciones gravadas y no gravadas
                importe(cero),                          // 18 IGV de esa base
                importe(cero),                          // 19 Base destinada a operaciones no gravadas
                importe(cero),                          // 20 IGV de esa base
                importe(r.noGravado()),                 // 21 Valor de las adquisiciones no gravadas
                importe(cero),                          // 22 ISC
                importe(cero),                          // 23 ICBPER
                importe(cero),                          // 24 Otros tributos y cargos
                importe(r.total()),                     // 25 Importe total
                r.moneda(),                             // 26 Moneda
                "",                                     // 27 Tipo de cambio
                "", "", "", "", "",                     // 28-32 Documento modificado (no aplica)
                "",                                     // 33 Clasificación de bienes y servicios
                "", "", ""                              // 34-36 Proyecto, % de participación, IMB
        ));
    }

    private static String importe(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fecha(LocalDate fecha) {
        return FECHA.format(fecha);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.replace(SEPARADOR, " ").replaceAll("\\s+", " ").trim();
    }
}
