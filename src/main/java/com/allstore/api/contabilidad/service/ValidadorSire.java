package com.allstore.api.contabilidad.service;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.contabilidad.dto.ValidacionSireResponse;
import com.allstore.api.contabilidad.dto.ValidacionSireResponse.Inconsistencia;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Revisa un archivo de reemplazo del SIRE con las reglas paramétricas de la estructura oficial que
 * publica SUNAT ("Estructura del Reemplazo RVIE", Anexo 3 de la RS 112-2021, y "Estructura del
 * Reemplazo RCE", Anexo 11 de la RS 040-2022): nombre del archivo, cantidad de campos, largo y tipo de
 * cada uno, fechas, importes, documentos de identidad, cuadre del total y duplicados. Es una revisión
 * previa: el validador oficial (PVSIRE) puede aplicar además reglas contra la información de SUNAT.
 */
public final class ValidadorSire {

    private static final Pattern IMPORTE = Pattern.compile("-?\\d{1,12}(\\.\\d{1,2})?");
    private static final Pattern TIPO_CAMBIO = Pattern.compile("\\d\\.\\d{3}");
    private static final Pattern ALFANUMERICO = Pattern.compile("[A-Za-z0-9]+");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    /** Diferencia tolerada entre el IGV informado y el 18 % de la base (redondeos por línea del comprobante). */
    private static final BigDecimal TOLERANCIA_IGV = BigDecimal.ONE;

    private static final String[] CAMPOS_RVIE = {"", "RUC", "Razón social", "Periodo", "CAR SUNAT", "Fecha de emisión",
            "Fecha Vcto/Pago", "Tipo CP", "Serie", "Número", "Número final", "Tipo doc. identidad", "Número doc. identidad",
            "Razón social del cliente", "Valor exportación", "BI gravada", "Dscto BI", "IGV", "Dscto IGV", "Exonerado",
            "Inafecto", "ISC", "BI IVAP", "IVAP", "ICBPER", "Otros tributos", "Total CP", "Moneda", "Tipo de cambio",
            "Fecha doc. modificado", "Tipo CP modificado", "Serie CP modificado", "Número CP modificado", "ID proyecto"};

    private static final String[] CAMPOS_RCE = {"", "RUC", "Razón social", "Periodo", "CAR SUNAT", "Fecha de emisión",
            "Fecha Vcto/Pago", "Tipo CP", "Serie", "Año DUA/DSI", "Número", "Número final", "Tipo doc. identidad",
            "Número doc. identidad", "Razón social del proveedor", "BI gravado DG", "IGV DG", "BI gravado DGNG", "IGV DGNG",
            "BI gravado DNG", "IGV DNG", "Valor adq. no gravada", "ISC", "ICBPER", "Otros tributos", "Total CP", "Moneda",
            "Tipo de cambio", "Fecha doc. modificado", "Tipo CP modificado", "Serie CP modificado", "Código DAM/DSI",
            "Número CP modificado", "Clasificación de bienes y servicios", "ID proyecto", "% participación", "IMB",
            "CAR original", "Campo 38", "Campo 39", "Campo 40", "Campo 41"};

    private ValidadorSire() {
    }

    public static ValidacionSireResponse rvie(ArchivoRvie.Archivo archivo) {
        return new Revision("RVIE", "140400", archivo, CAMPOS_RVIE).rvie();
    }

    public static ValidacionSireResponse rce(ArchivoRvie.Archivo archivo) {
        return new Revision("RCE", "080400", archivo, CAMPOS_RCE).rce();
    }

    /** Estado de una revisión: las líneas del archivo y lo encontrado. */
    private static final class Revision {
        private final String registro;
        private final String libro;
        private final ArchivoRvie.Archivo archivo;
        private final String[] nombres;
        private final List<Inconsistencia> errores = new ArrayList<>();
        private final List<String> lineas;
        private String ruc;
        private YearMonth periodo;
        private int fila;
        private String[] campos;

        Revision(String registro, String libro, ArchivoRvie.Archivo archivo, String[] nombres) {
            this.registro = registro;
            this.libro = libro;
            this.archivo = archivo;
            this.nombres = nombres;
            String texto = new String(archivo.contenido(), StandardCharsets.UTF_8);
            if (texto.startsWith("\uFEFF")) {
                errores.add(new Inconsistencia(0, 0, "Archivo", "BOM", "El archivo no debe empezar con la marca BOM de UTF-8"));
                texto = texto.substring(1);
            }
            this.lineas = texto.isEmpty() ? List.of() : List.of(texto.split("\r?\n"));
        }

        ValidacionSireResponse rvie() {
            nombre();
            Set<String> vistos = new HashSet<>();
            for (fila = 1; fila <= lineas.size(); fila++) {
                campos = lineas.get(fila - 1).split("\\|", -1);
                if (campos.length < 33 || campos.length > 57) {
                    error(0, campos.length + " campos", "Debe tener entre 33 y 57 campos separados por |");
                    continue;
                }
                cabecera();
                fecha(5, true);
                fechaEnPeriodo(5, false);
                fecha(6, false);
                tipoComprobante(7);
                texto(8, 1, 20, true, ALFANUMERICO);
                texto(9, 1, 20, true, Pattern.compile("[0-9]+"));
                texto(10, 0, 20, false, null);
                documento(11, 12);
                texto(13, 0, 1500, false, null);
                boolean nota = "07".equals(valor(7));
                for (int c = 14; c <= 26; c++) {
                    importe(c, nota);
                }
                cuadre(26, new int[]{14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25});
                igv(15, 17);
                moneda(27, 28);
                modificado(7, 29, 30, 31, 32);
                texto(33, 0, 50, false, null);
                duplicado(vistos, valor(7) + "-" + valor(8) + "-" + valor(9));
            }
            return resultado();
        }

        ValidacionSireResponse rce() {
            nombre();
            Set<String> vistos = new HashSet<>();
            for (fila = 1; fila <= lineas.size(); fila++) {
                campos = lineas.get(fila - 1).split("\\|", -1);
                if (campos.length < 41 || campos.length > 80) {
                    error(0, campos.length + " campos", "Debe tener entre 41 y 80 campos separados por | (del 37 al 41 vacíos)");
                    continue;
                }
                cabecera();
                fecha(5, true);
                fechaEnPeriodo(5, true);
                fecha(6, false);
                tipoComprobante(7);
                texto(8, 1, 20, true, ALFANUMERICO);
                texto(9, 0, 4, false, Pattern.compile("[0-9]{4}"));
                texto(10, 1, 20, true, null);
                texto(11, 0, 20, false, null);
                documento(12, 13);
                if (valor(12).isEmpty()) {
                    error(12, "", "El tipo de documento del proveedor es obligatorio");
                }
                texto(14, 1, 1500, true, null);
                boolean nota = "07".equals(valor(7));
                for (int c = 15; c <= 25; c++) {
                    importe(c, nota);
                }
                cuadre(25, new int[]{15, 16, 17, 18, 19, 20, 21, 22, 23, 24});
                igv(15, 16);
                igv(17, 18);
                igv(19, 20);
                moneda(26, 27);
                modificado(7, 28, 29, 30, 32);
                texto(31, 0, 3, false, null);
                texto(33, 0, 1, false, Pattern.compile("[1-5]"));
                for (int c = 37; c <= 41; c++) {
                    if (!valor(c).isEmpty()) {
                        error(c, valor(c), "Debe ir vacío en el reemplazo de la propuesta");
                    }
                }
                duplicado(vistos, valor(7) + "-" + valor(8) + "-" + valor(10) + "-" + valor(13));
            }
            return resultado();
        }

        // ---------- Reglas ----------

        /** LE + RUC + AAAA + MM + 00 + libro + CC + O + I + M + 2, y que coincida con el contenido. */
        private void nombre() {
            String sinExtension = archivo.nombre().replaceFirst("\\.(txt|zip)$", "");
            var m = Pattern.compile("LE(\\d{11})(\\d{4})(\\d{2})00" + libro + "(0[0-6])([012])([01])([12])2").matcher(sinExtension);
            if (!m.matches()) {
                errores.add(new Inconsistencia(0, 0, "Nombre del archivo", archivo.nombre(), "No cumple la estructura LE + RUC + AAAA"
                        + " + MM + 00 + " + libro + " + oportunidad + operación + contenido + moneda + 2"));
                return;
            }
            ruc = m.group(1);
            try {
                periodo = YearMonth.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            } catch (RuntimeException e) {
                errores.add(new Inconsistencia(0, 0, "Nombre del archivo", archivo.nombre(), "El mes del nombre no es válido"));
            }
            boolean conInformacion = m.group(6).equals("1");
            if (conInformacion == lineas.isEmpty()) {
                errores.add(new Inconsistencia(0, 0, "Nombre del archivo", m.group(6), conInformacion
                        ? "El nombre indica que hay información (1) pero el archivo está vacío"
                        : "El nombre indica que no hay información (0) pero el archivo tiene filas"));
            }
        }

        /** Campos 1 a 4: RUC (el del nombre, con dígito verificador), razón social, periodo y CAR vacío. */
        private void cabecera() {
            String rucFila = valor(1);
            TipoDocumentoIdentidad.RUC.validar(rucFila).ifPresent(motivo -> error(1, rucFila, motivo));
            if (ruc != null && !ruc.equals(rucFila)) {
                error(1, rucFila, "No coincide con el RUC del nombre del archivo (" + ruc + ")");
            }
            texto(2, 1, 1500, true, null);
            String p = valor(3);
            if (!p.matches("\\d{4}(0[1-9]|1[0-2])")) {
                error(3, p, "El periodo debe ser AAAAMM");
            } else if (periodo != null && !p.equals(periodo.toString().replace("-", ""))) {
                error(3, p, "No coincide con el periodo del nombre del archivo");
            }
            if (!valor(4).isEmpty()) {
                error(4, valor(4), "El CAR debe ir vacío en el reemplazo de la propuesta");
            }
        }

        private void fecha(int c, boolean obligatoria) {
            String v = valor(c);
            if (v.isEmpty()) {
                if (obligatoria) {
                    error(c, v, "La fecha es obligatoria");
                }
                return;
            }
            try {
                LocalDate.parse(v, FECHA);
            } catch (DateTimeParseException e) {
                error(c, v, "La fecha debe ser DD/MM/AAAA y existir");
            }
        }

        /** Ventas: del mismo periodo. Compras: hasta el fin del periodo y no más de 12 meses antes. */
        private void fechaEnPeriodo(int c, boolean compras) {
            if (periodo == null) {
                return;
            }
            try {
                YearMonth mes = YearMonth.from(LocalDate.parse(valor(c), FECHA));
                if (!compras && !mes.equals(periodo)) {
                    error(c, valor(c), "La fecha de emisión debe ser del periodo " + periodo);
                } else if (compras && (mes.isAfter(periodo) || mes.isBefore(periodo.minusMonths(12)))) {
                    error(c, valor(c), "La fecha de emisión debe ser del periodo o de hasta 12 meses antes");
                }
            } catch (DateTimeParseException e) {
                // ya se informó al revisar la fecha
            }
        }

        private void tipoComprobante(int c) {
            if (!valor(c).matches("\\d{2}")) {
                error(c, valor(c), "El tipo de comprobante son 2 dígitos (tabla 10)");
            }
        }

        private void texto(int c, int minimo, int maximo, boolean obligatorio, Pattern formato) {
            String v = valor(c);
            if (v.isEmpty()) {
                if (obligatorio || minimo > 0) {
                    error(c, v, "Es obligatorio");
                }
                return;
            }
            if (v.length() > maximo) {
                error(c, v, "Supera los " + maximo + " caracteres");
            }
            if (formato != null && !formato.matcher(v).matches()) {
                error(c, v, "Formato no válido");
            }
        }

        /** Tipo de documento (tabla 2) y número: RUC con dígito verificador, DNI de 8 dígitos. */
        private void documento(int tipo, int numero) {
            String t = valor(tipo);
            String n = valor(numero);
            if (t.isEmpty()) {
                if (!n.isEmpty()) {
                    error(tipo, t, "Falta el tipo de documento del número " + n);
                }
                return;
            }
            if (!t.matches("[0-9A-Z]")) {
                error(tipo, t, "El tipo de documento es 1 carácter (tabla 2)");
            }
            if (n.isEmpty() || n.length() > 15) {
                error(numero, n, "El número de documento es obligatorio y tiene hasta 15 caracteres");
            } else if (t.equals("6")) {
                TipoDocumentoIdentidad.RUC.validar(n).ifPresent(motivo -> error(numero, n, motivo));
            } else if (t.equals("1") && !n.matches("\\d{8}")) {
                error(numero, n, "El DNI debe tener 8 dígitos");
            }
        }

        private void importe(int c, boolean admiteNegativo) {
            String v = valor(c);
            if (v.isEmpty()) {
                return;
            }
            if (!IMPORTE.matcher(v).matches()) {
                error(c, v, "Hasta 12 enteros y 2 decimales, con punto y sin separador de miles");
            } else if (!admiteNegativo && v.startsWith("-")) {
                error(c, v, "Solo las notas de crédito llevan importes negativos");
            }
        }

        private void cuadre(int total, int[] partes) {
            try {
                BigDecimal suma = BigDecimal.ZERO;
                for (int c : partes) {
                    suma = suma.add(numero(c));
                }
                if (suma.compareTo(numero(total)) != 0) {
                    error(total, valor(total), "El total no es la suma de sus importes (" + suma.toPlainString() + ")");
                }
            } catch (NumberFormatException e) {
                // el formato ya se informó
            }
        }

        /** El IGV debe ser el 18 % de su base (con una tolerancia por redondeos). */
        private void igv(int base, int impuesto) {
            try {
                BigDecimal esperado = numero(base).multiply(TASA_IGV);
                if (esperado.subtract(numero(impuesto)).abs().compareTo(TOLERANCIA_IGV) > 0) {
                    error(impuesto, valor(impuesto), "No corresponde al 18 % de la base (" + valor(base) + ")");
                }
            } catch (NumberFormatException e) {
                // el formato ya se informó
            }
        }

        private void moneda(int moneda, int tipoCambio) {
            String m = valor(moneda);
            if (!m.matches("[A-Z]{3}")) {
                error(moneda, m, "La moneda son 3 letras (PEN, USD)");
            } else if (!m.equals("PEN") && !TIPO_CAMBIO.matcher(valor(tipoCambio)).matches()) {
                error(tipoCambio, valor(tipoCambio), "Con moneda distinta a PEN, el tipo de cambio es obligatorio (0.000)");
            }
        }

        /** Notas de crédito y débito: el comprobante que modifican es obligatorio. */
        private void modificado(int tipo, int fecha, int tipoModificado, int serie, int numero) {
            if (!valor(tipo).equals("07") && !valor(tipo).equals("08")) {
                return;
            }
            fecha(fecha, true);
            if (!valor(tipoModificado).matches("\\d{2}")) {
                error(tipoModificado, valor(tipoModificado), "Una nota debe indicar el tipo del comprobante que modifica");
            }
            texto(serie, 1, 20, true, null);
            texto(numero, 1, 20, true, null);
        }

        private void duplicado(Set<String> vistos, String clave) {
            if (!vistos.add(clave)) {
                error(0, clave, "Comprobante repetido en el archivo");
            }
        }

        // ---------- Apoyo ----------

        private String valor(int campo) {
            return campo <= campos.length ? campos[campo - 1] : "";
        }

        private BigDecimal numero(int campo) {
            String v = valor(campo);
            return v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v);
        }

        private void error(int campo, String valor, String mensaje) {
            errores.add(new Inconsistencia(fila, campo, campo == 0 ? "Línea" : campo < nombres.length ? nombres[campo] : "Campo " + campo,
                    valor, mensaje));
        }

        private ValidacionSireResponse resultado() {
            return new ValidacionSireResponse(registro, archivo.nombre(), lineas.size(), errores.isEmpty(), List.copyOf(errores));
        }
    }
}
