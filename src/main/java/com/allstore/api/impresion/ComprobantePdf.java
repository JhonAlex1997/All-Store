package com.allstore.api.impresion;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dibuja el comprobante en PDF. Solo presentación: los datos llegan resueltos en
 * {@link DatosImpresion}.
 *
 * <p>El ticket se arma primero completo para medir su alto y luego se crea una página de 80 mm de
 * ancho con ese alto exacto: la impresora térmica corta donde termina, sin papel en blanco.
 */
@Component
public class ComprobantePdf {

    private static final Color GRIS = new Color(90, 90, 90);
    private static final Color GRIS_CLARO = new Color(225, 225, 225);
    private static final Color FONDO_ENCABEZADO = new Color(240, 240, 240);
    private static final Color MARCA = new Color(215, 215, 215);

    /** 80 mm en puntos PDF (1 pulgada = 72 puntos = 25.4 mm). */
    private static final float ANCHO_TICKET = 80 * 72 / 25.4f;
    private static final float MARGEN_TICKET = 8;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public byte[] generar(DatosImpresion d, FormatoImpresion formato) {
        try {
            return formato == FormatoImpresion.TICKET ? ticket(d) : a4(d);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF de " + d.numero(), e);
        }
    }

    // =====================================================================
    // Ticket 80 mm
    // =====================================================================

    private byte[] ticket(DatosImpresion d) throws DocumentException, IOException {
        Font normal = fuente(7.5f, Font.NORMAL);
        Font negrita = fuente(7.5f, Font.BOLD);
        Font chica = fuente(6.5f, Font.NORMAL, GRIS);
        Font titulo = fuente(9, Font.BOLD);

        PdfPTable t = new PdfPTable(1);
        t.setTotalWidth(ANCHO_TICKET - 2 * MARGEN_TICKET);
        t.setLockedWidth(true);

        DatosImpresion.Emisor e = d.emisor();
        centrado(t, e.nombreVisible(), fuente(10, Font.BOLD));
        if (e.nombreComercial() != null) {
            centrado(t, e.razonSocial(), normal);
        }
        centrado(t, "RUC " + e.ruc(), negrita);
        centrado(t, e.direccionFiscal(), chica);
        if (d.direccionEstablecimiento() != null && !d.direccionEstablecimiento().equals(e.direccionFiscal())) {
            centrado(t, d.establecimiento() + ": " + d.direccionEstablecimiento(), chica);
        }
        contacto(e).ifPresent(c -> centrado(t, c, chica));
        separador(t);
        centrado(t, d.titulo(), titulo);
        centrado(t, d.numero(), titulo);
        separador(t);

        dosColumnas(t, "Fecha:", FECHA_HORA.format(d.fechaEmision()), normal, normal);
        dosColumnas(t, "Cajero:", d.cajero(), normal, normal);
        DatosImpresion.Cliente c = d.cliente();
        if (c.tipoDocumento() != null) {
            dosColumnas(t, c.tipoDocumento() + ":", c.numeroDocumento(), normal, normal);
        }
        dosColumnas(t, "Cliente:", c.nombre(), normal, normal);
        if (c.direccion() != null) {
            dosColumnas(t, "Dirección:", c.direccion(), normal, normal);
        }
        if (d.referencia() != null) {
            dosColumnas(t, "Modifica:", d.referencia().documento(), normal, negrita);
            dosColumnas(t, "Motivo:", d.referencia().motivo(), normal, normal);
            dosColumnas(t, "Sustento:", d.referencia().sustento(), normal, normal);
        }
        separador(t);

        for (DatosImpresion.Linea l : d.lineas()) {
            texto(t, l.descripcion(), normal);
            dosColumnas(t, "  " + l.cantidad() + " x " + dinero(l.precioUnitario()), dinero(l.importe()), chica, normal);
            if (l.descuento().signum() > 0) {
                dosColumnas(t, "  Descuento", "-" + dinero(l.descuento()), chica, chica);
            }
        }
        separador(t);

        totales(d).forEach(fila -> dosColumnas(t, fila[0], fila[1], normal, normal));
        dosColumnas(t, "TOTAL S/", dinero(d.total()), fuente(9, Font.BOLD), fuente(9, Font.BOLD));
        texto(t, "SON: " + d.montoEnLetras(), chica);
        separador(t);

        String prefijoPago = d.referencia() != null ? "Devolución - " : "";
        for (DatosImpresion.Pago p : d.pagos()) {
            dosColumnas(t, prefijoPago + p.metodo() + (p.referencia() != null ? " (" + p.referencia() + ")" : ""),
                    dinero(p.monto()), normal, normal);
        }
        if (d.vuelto().signum() > 0) {
            dosColumnas(t, "Vuelto", dinero(d.vuelto()), normal, normal);
        }
        if (d.observacion() != null) {
            texto(t, "Obs.: " + d.observacion(), chica);
        }

        if (d.codigoQr() != null) {
            PdfPCell qr = new PdfPCell(qr(d.codigoQr(), 95));
            qr.setBorder(Rectangle.NO_BORDER);
            qr.setHorizontalAlignment(Element.ALIGN_CENTER);
            qr.setPaddingTop(6);
            t.addCell(qr);
        }
        if (d.hash() != null) {
            centrado(t, "Hash: " + d.hash(), chica);
        }
        centrado(t, d.leyenda(), chica);
        if (d.referencia() == null) {
            centrado(t, "¡Gracias por su compra!", negrita);
        }

        float alto = t.getTotalHeight() + 2 * MARGEN_TICKET + 4;
        Document doc = new Document(new Rectangle(ANCHO_TICKET, alto), MARGEN_TICKET, MARGEN_TICKET, MARGEN_TICKET, MARGEN_TICKET);
        return escribir(doc, d.marcaDeAgua(), 16, () -> doc.add(t));
    }

    // =====================================================================
    // A4
    // =====================================================================

    private byte[] a4(DatosImpresion d) throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        Font normal = fuente(9, Font.NORMAL);
        Font negrita = fuente(9, Font.BOLD);
        Font chica = fuente(7.5f, Font.NORMAL, GRIS);

        return escribir(doc, d.marcaDeAgua(), 38, () -> {
            // Encabezado: emisor a la izquierda, recuadro con RUC, tipo y número a la derecha.
            PdfPTable cabecera = new PdfPTable(new float[]{62, 38});
            cabecera.setWidthPercentage(100);
            DatosImpresion.Emisor e = d.emisor();
            PdfPCell emisor = sinBorde();
            emisor.addElement(new Paragraph(e.nombreVisible(), fuente(15, Font.BOLD)));
            if (e.nombreComercial() != null) {
                emisor.addElement(new Paragraph(e.razonSocial(), negrita));
            }
            emisor.addElement(new Paragraph(e.direccionFiscal(), normal));
            if (d.direccionEstablecimiento() != null && !d.direccionEstablecimiento().equals(e.direccionFiscal())) {
                emisor.addElement(new Paragraph(d.establecimiento() + ": " + d.direccionEstablecimiento(), normal));
            }
            contacto(e).ifPresent(c -> emisor.addElement(new Paragraph(c, chica)));
            cabecera.addCell(emisor);

            PdfPTable recuadro = new PdfPTable(1);
            celdaCentrada(recuadro, "R.U.C. N° " + e.ruc(), fuente(12, Font.BOLD), null);
            celdaCentrada(recuadro, d.titulo(), fuente(11, Font.BOLD), FONDO_ENCABEZADO);
            celdaCentrada(recuadro, d.numero(), fuente(12, Font.BOLD), null);
            PdfPCell contenedor = new PdfPCell(recuadro);
            contenedor.setBorder(Rectangle.BOX);
            contenedor.setBorderWidth(1.2f);
            contenedor.setPadding(6);
            cabecera.addCell(contenedor);
            doc.add(cabecera);
            doc.add(espacio(8));

            // Cliente y fecha
            PdfPTable datos = new PdfPTable(new float[]{16, 50, 16, 18});
            datos.setWidthPercentage(100);
            DatosImpresion.Cliente c = d.cliente();
            etiquetaValor(datos, "Señor(es):", c.nombre(), negrita, normal);
            etiquetaValor(datos, "Fecha emisión:", FECHA.format(d.fechaEmision()), negrita, normal);
            etiquetaValor(datos, c.tipoDocumento() != null ? c.tipoDocumento() + ":" : "Documento:",
                    c.tipoDocumento() != null ? c.numeroDocumento() : "-", negrita, normal);
            etiquetaValor(datos, "Hora:", HORA.format(d.fechaEmision()), negrita, normal);
            etiquetaValor(datos, "Dirección:", c.direccion() != null ? c.direccion() : "-", negrita, normal);
            etiquetaValor(datos, "Moneda:", "SOLES", negrita, normal);
            if (d.referencia() != null) {
                etiquetaValor(datos, "Modifica a:", d.referencia().documento(), negrita, normal);
                etiquetaValor(datos, "Motivo:", d.referencia().motivo(), negrita, normal);
                PdfPCell sustento = celda("Sustento: " + d.referencia().sustento(), normal, Rectangle.NO_BORDER);
                sustento.setColspan(4);
                datos.addCell(sustento);
            }
            doc.add(datos);
            doc.add(espacio(8));

            // Detalle
            PdfPTable detalle = new PdfPTable(new float[]{7, 7, 17, 40, 10, 8, 11});
            detalle.setWidthPercentage(100);
            detalle.setHeaderRows(1);
            for (String encabezado : new String[]{"Cant.", "Unid.", "Código", "Descripción", "P. Unit.", "Dscto.", "Importe"}) {
                PdfPCell h = celda(encabezado, negrita, Rectangle.BOTTOM);
                h.setBackgroundColor(FONDO_ENCABEZADO);
                h.setHorizontalAlignment(Element.ALIGN_CENTER);
                detalle.addCell(h);
            }
            for (DatosImpresion.Linea l : d.lineas()) {
                detalle.addCell(derecha(String.valueOf(l.cantidad()), normal));
                detalle.addCell(celda(l.unidad(), normal, Rectangle.BOTTOM));
                detalle.addCell(celda(l.codigo(), chica, Rectangle.BOTTOM));
                detalle.addCell(celda(l.descripcion(), normal, Rectangle.BOTTOM));
                detalle.addCell(derecha(dinero(l.precioUnitario()), normal));
                detalle.addCell(derecha(l.descuento().signum() > 0 ? dinero(l.descuento()) : "", normal));
                detalle.addCell(derecha(dinero(l.importe()), normal));
            }
            doc.add(detalle);
            doc.add(espacio(6));

            // Importe en letras y pagos a la izquierda; totales a la derecha.
            PdfPTable resumen = new PdfPTable(new float[]{60, 40});
            resumen.setWidthPercentage(100);
            PdfPCell izquierda = sinBorde();
            izquierda.addElement(new Paragraph("SON: " + d.montoEnLetras(), negrita));
            // En una nota de crédito el "pago" es el dinero que se le devuelve al cliente.
            StringBuilder pagos = new StringBuilder(d.referencia() != null ? "Devolución al cliente: " : "Forma de pago: Contado - ");
            d.pagos().forEach(p -> pagos.append(p.metodo()).append(" ").append(dinero(p.monto())).append("  "));
            izquierda.addElement(new Paragraph(pagos.toString().trim(), normal));
            if (d.observacion() != null) {
                izquierda.addElement(new Paragraph("Observación: " + d.observacion(), normal));
            }
            resumen.addCell(izquierda);
            PdfPTable totales = new PdfPTable(new float[]{60, 40});
            totales(d).forEach(fila -> {
                totales.addCell(celda(fila[0], normal, Rectangle.NO_BORDER));
                totales.addCell(derechaSinBorde(fila[1], normal));
            });
            PdfPCell etiquetaTotal = celda("IMPORTE TOTAL S/", fuente(10, Font.BOLD), Rectangle.TOP);
            totales.addCell(etiquetaTotal);
            PdfPCell valorTotal = celda(dinero(d.total()), fuente(10, Font.BOLD), Rectangle.TOP);
            valorTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totales.addCell(valorTotal);
            PdfPCell derechaTotales = new PdfPCell(totales);
            derechaTotales.setBorder(Rectangle.NO_BORDER);
            resumen.addCell(derechaTotales);
            doc.add(resumen);
            doc.add(espacio(14));

            // Pie: QR, hash y leyenda.
            PdfPTable pie = new PdfPTable(new float[]{18, 82});
            pie.setWidthPercentage(100);
            PdfPCell celdaQr = sinBorde();
            if (d.codigoQr() != null) {
                celdaQr.addElement(qr(d.codigoQr(), 90));
            }
            pie.addCell(celdaQr);
            PdfPCell textos = sinBorde();
            if (d.hash() != null) {
                textos.addElement(new Paragraph("Hash: " + d.hash(), chica));
            }
            textos.addElement(new Paragraph(d.leyenda(), normal));
            textos.addElement(new Paragraph("Atendido por: " + d.cajero(), chica));
            pie.addCell(textos);
            doc.add(pie);
        });
    }

    // =====================================================================
    // Comunes
    // =====================================================================

    /** Filas de totales que aplican (las de valor cero no se imprimen, salvo IGV). */
    private static List<String[]> totales(DatosImpresion d) {
        List<String[]> filas = new ArrayList<>();
        if (d.opGravadas().signum() > 0) {
            filas.add(new String[]{"Op. gravada", dinero(d.opGravadas())});
        }
        if (d.opExoneradas().signum() > 0) {
            filas.add(new String[]{"Op. exonerada", dinero(d.opExoneradas())});
        }
        if (d.opInafectas().signum() > 0) {
            filas.add(new String[]{"Op. inafecta", dinero(d.opInafectas())});
        }
        if (d.totalDescuento().signum() > 0) {
            filas.add(new String[]{"Descuentos", dinero(d.totalDescuento())});
        }
        filas.add(new String[]{"IGV 18%", dinero(d.totalIgv())});
        return filas;
    }

    private interface Contenido {
        void agregar() throws DocumentException, IOException;
    }

    private static byte[] escribir(Document doc, String marcaDeAgua, float tamanoMarca, Contenido contenido)
            throws DocumentException, IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, salida);
        if (marcaDeAgua != null) {
            writer.setPageEvent(new MarcaDeAgua(marcaDeAgua, tamanoMarca));
        }
        doc.open();
        contenido.agregar();
        doc.close();
        return salida.toByteArray();
    }

    /** Texto en diagonal, detrás del contenido (ANULADO, SIN VALIDEZ TRIBUTARIA). */
    private static class MarcaDeAgua extends PdfPageEventHelper {
        private final String texto;
        private final float tamano;

        MarcaDeAgua(String texto, float tamano) {
            this.texto = texto;
            this.tamano = tamano;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle pagina = document.getPageSize();
            float angulo = (float) Math.toDegrees(Math.atan2(pagina.getHeight(), pagina.getWidth()));
            ColumnText.showTextAligned(writer.getDirectContentUnder(), Element.ALIGN_CENTER,
                    new Phrase(texto, new Font(Font.HELVETICA, tamano, Font.BOLD, MARCA)),
                    pagina.getWidth() / 2, pagina.getHeight() / 2, angulo);
        }
    }

    private static Image qr(String texto, float lado) throws IOException {
        Image imagen = Image.getInstance(CodigoQr.png(texto, 300));
        imagen.scaleToFit(lado, lado);
        imagen.setAlignment(Element.ALIGN_CENTER);
        return imagen;
    }

    private static Optional<String> contacto(DatosImpresion.Emisor e) {
        String texto = Stream.of(e.telefono() != null ? "Tel. " + e.telefono() : null, e.email())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("  ·  "));
        return texto.isEmpty() ? Optional.empty() : Optional.of(texto);
    }

    private static void centrado(PdfPTable t, String texto, Font fuente) {
        PdfPCell c = celda(texto, fuente, Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    private static void texto(PdfPTable t, String texto, Font fuente) {
        t.addCell(celda(texto, fuente, Rectangle.NO_BORDER));
    }

    private static void dosColumnas(PdfPTable t, String izquierda, String derecha, Font fIzq, Font fDer) {
        PdfPTable fila = new PdfPTable(new float[]{55, 45});
        fila.setWidthPercentage(100);
        fila.addCell(celda(izquierda, fIzq, Rectangle.NO_BORDER));
        fila.addCell(derechaSinBorde(derecha, fDer));
        PdfPCell contenedor = new PdfPCell(fila);
        contenedor.setBorder(Rectangle.NO_BORDER);
        contenedor.setPadding(0);
        t.addCell(contenedor);
    }

    private static void separador(PdfPTable t) {
        PdfPCell linea = new PdfPCell(new Phrase(" ", fuente(3, Font.NORMAL)));
        linea.setBorder(Rectangle.BOTTOM);
        linea.setBorderColor(GRIS);
        linea.setBorderWidth(0.5f);
        linea.setPaddingBottom(2);
        t.addCell(linea);
        PdfPCell aire = new PdfPCell(new Phrase(" ", fuente(3, Font.NORMAL)));
        aire.setBorder(Rectangle.NO_BORDER);
        t.addCell(aire);
    }

    private static void etiquetaValor(PdfPTable t, String etiqueta, String valor, Font fEtiqueta, Font fValor) {
        t.addCell(celda(etiqueta, fEtiqueta, Rectangle.NO_BORDER));
        t.addCell(celda(valor, fValor, Rectangle.NO_BORDER));
    }

    private static void celdaCentrada(PdfPTable t, String texto, Font fuente, Color fondo) {
        PdfPCell c = celda(texto, fuente, Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(4);
        if (fondo != null) {
            c.setBackgroundColor(fondo);
        }
        t.addCell(c);
    }

    private static PdfPCell celda(String texto, Font fuente, int borde) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        c.setBorder(borde);
        c.setBorderColor(GRIS_CLARO);
        c.setPadding(2);
        return c;
    }

    private static PdfPCell derecha(String texto, Font fuente) {
        PdfPCell c = celda(texto, fuente, Rectangle.BOTTOM);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static PdfPCell derechaSinBorde(String texto, Font fuente) {
        PdfPCell c = celda(texto, fuente, Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Paragraph espacio(float alto) {
        Paragraph p = new Paragraph(" ", fuente(1, Font.NORMAL));
        p.setSpacingAfter(alto);
        return p;
    }

    private static Font fuente(float tamano, int estilo) {
        return new Font(Font.HELVETICA, tamano, estilo);
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return new Font(Font.HELVETICA, tamano, estilo, color);
    }

    /** 1,234.50 (formato usado en Perú: coma de miles, punto decimal). */
    static String dinero(BigDecimal monto) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return formato.format(monto);
    }
}
