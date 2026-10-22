package com.allstore.api.impresion;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

import static com.allstore.api.impresion.ComprobantePdf.FONDO_ENCABEZADO;
import static com.allstore.api.impresion.ComprobantePdf.GRIS;
import static com.allstore.api.impresion.ComprobantePdf.celda;
import static com.allstore.api.impresion.ComprobantePdf.celdaCentrada;
import static com.allstore.api.impresion.ComprobantePdf.contacto;
import static com.allstore.api.impresion.ComprobantePdf.derecha;
import static com.allstore.api.impresion.ComprobantePdf.escribir;
import static com.allstore.api.impresion.ComprobantePdf.espacio;
import static com.allstore.api.impresion.ComprobantePdf.etiquetaValor;
import static com.allstore.api.impresion.ComprobantePdf.fuente;
import static com.allstore.api.impresion.ComprobantePdf.qr;
import static com.allstore.api.impresion.ComprobantePdf.sinBorde;

/**
 * Representación impresa de la guía de remisión remitente (A4), con el mismo estilo que los
 * comprobantes: emisor y recuadro con RUC y número arriba, datos del traslado, prendas y, cuando
 * SUNAT ya la aceptó, el código QR con el enlace de consulta de su constancia.
 */
@Component
public class GuiaPdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generar(DatosGuia d) {
        try {
            return a4(d);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF de " + d.numero(), e);
        }
    }

    private byte[] a4(DatosGuia d) throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        Font normal = fuente(9, Font.NORMAL);
        Font negrita = fuente(9, Font.BOLD);
        Font chica = fuente(7.5f, Font.NORMAL, GRIS);

        return escribir(doc, d.marcaDeAgua(), 38, () -> {
            PdfPTable cabecera = new PdfPTable(new float[]{60, 40});
            cabecera.setWidthPercentage(100);
            DatosImpresion.Emisor e = d.emisor();
            PdfPCell emisor = sinBorde();
            emisor.addElement(new Paragraph(e.nombreVisible(), fuente(15, Font.BOLD)));
            if (e.nombreComercial() != null) {
                emisor.addElement(new Paragraph(e.razonSocial(), negrita));
            }
            emisor.addElement(new Paragraph(e.direccionFiscal(), normal));
            contacto(e).ifPresent(c -> emisor.addElement(new Paragraph(c, chica)));
            cabecera.addCell(emisor);
            PdfPTable recuadro = new PdfPTable(1);
            celdaCentrada(recuadro, "R.U.C. N° " + e.ruc(), fuente(12, Font.BOLD), null);
            celdaCentrada(recuadro, d.titulo(), fuente(10, Font.BOLD), FONDO_ENCABEZADO);
            celdaCentrada(recuadro, d.numero(), fuente(12, Font.BOLD), null);
            PdfPCell contenedor = new PdfPCell(recuadro);
            contenedor.setBorder(Rectangle.BOX);
            contenedor.setBorderWidth(1.2f);
            contenedor.setPadding(6);
            cabecera.addCell(contenedor);
            doc.add(cabecera);
            doc.add(espacio(8));

            PdfPTable datos = new PdfPTable(new float[]{18, 42, 18, 22});
            datos.setWidthPercentage(100);
            etiquetaValor(datos, "Fecha de emisión:", FECHA_HORA.format(d.fechaEmision()), negrita, normal);
            etiquetaValor(datos, "Inicio del traslado:", FECHA.format(d.fechaTraslado()), negrita, normal);
            etiquetaValor(datos, "Motivo:", d.motivo(), negrita, normal);
            etiquetaValor(datos, "Modalidad:", d.modalidad(), negrita, normal);
            etiquetaValor(datos, "Peso bruto:", d.pesoBrutoKg().setScale(3, RoundingMode.HALF_UP).toPlainString() + " KG", negrita, normal);
            etiquetaValor(datos, "Bultos:", d.numeroBultos() == null ? "-" : String.valueOf(d.numeroBultos()), negrita, normal);
            if (d.documentoRelacionado() != null) {
                etiquetaValor(datos, "Documento:", d.documentoRelacionado(), negrita, normal);
                etiquetaValor(datos, "", "", negrita, normal);
            }
            doc.add(datos);
            doc.add(espacio(6));

            doc.add(bloque("DESTINATARIO", d.destinatario(), negrita, normal));
            doc.add(bloque("PUNTO DE PARTIDA", d.partida(), negrita, normal));
            doc.add(bloque("PUNTO DE LLEGADA", d.llegada(), negrita, normal));
            doc.add(bloque("TRANSPORTE", String.join("\n", d.transporte()), negrita, normal));
            doc.add(espacio(6));

            PdfPTable detalle = new PdfPTable(new float[]{6, 20, 52, 10, 12});
            detalle.setWidthPercentage(100);
            detalle.setHeaderRows(1);
            for (String encabezado : new String[]{"N°", "Código", "Descripción", "Unid.", "Cantidad"}) {
                PdfPCell h = celda(encabezado, negrita, Rectangle.BOTTOM);
                h.setBackgroundColor(FONDO_ENCABEZADO);
                h.setHorizontalAlignment(Element.ALIGN_CENTER);
                detalle.addCell(h);
            }
            int unidades = 0;
            for (DatosGuia.Linea l : d.lineas()) {
                detalle.addCell(derecha(String.valueOf(l.item()), normal));
                detalle.addCell(celda(l.codigo(), chica, Rectangle.BOTTOM));
                detalle.addCell(celda(l.descripcion(), normal, Rectangle.BOTTOM));
                detalle.addCell(celda(l.unidad(), normal, Rectangle.BOTTOM));
                detalle.addCell(derecha(String.valueOf(l.cantidad()), normal));
                unidades += l.cantidad();
            }
            PdfPCell total = celda("Total de unidades", negrita, Rectangle.NO_BORDER);
            total.setColspan(4);
            total.setHorizontalAlignment(Element.ALIGN_RIGHT);
            detalle.addCell(total);
            detalle.addCell(derecha(String.valueOf(unidades), negrita));
            doc.add(detalle);
            if (d.observacion() != null) {
                doc.add(espacio(4));
                doc.add(new Paragraph("Observación: " + d.observacion(), normal));
            }
            doc.add(espacio(14));

            PdfPTable pie = new PdfPTable(new float[]{18, 82});
            pie.setWidthPercentage(100);
            PdfPCell celdaQr = sinBorde();
            if (d.enlaceQr() != null) {
                celdaQr.addElement(qr(d.enlaceQr(), 90));
            }
            pie.addCell(celdaQr);
            PdfPCell textos = sinBorde();
            textos.addElement(new Paragraph(d.leyenda(), normal));
            textos.addElement(new Paragraph("Emitido por: " + d.emitidoPor(), chica));
            pie.addCell(textos);
            doc.add(pie);
        });
    }

    /** Título en una franja gris y el texto debajo. */
    private static PdfPTable bloque(String titulo, String texto, Font negrita, Font normal) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingAfter(4);
        PdfPCell cabecera = celda(titulo, negrita, Rectangle.NO_BORDER);
        cabecera.setBackgroundColor(FONDO_ENCABEZADO);
        t.addCell(cabecera);
        t.addCell(celda(texto, normal, Rectangle.NO_BORDER));
        return t;
    }
}
