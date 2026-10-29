package com.allstore.api.producto.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.producto.dto.EtiquetasRequest;
import com.allstore.api.producto.entity.ProductoVariante;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.EAN13Writer;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.Rectangle;
import org.openpdf.text.Utilities;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * PDF de etiquetas con el código de barras EAN-13 de cada prenda (el mismo que lee la caja al
 * escanear), su modelo, color, talla y precio. Las barras se dibujan como vectores: salen nítidas
 * en cualquier impresora.
 * <ul>
 *   <li>ROLLO_50X25: una etiqueta por página de 50 × 25 mm, para impresoras térmicas de etiquetas.</li>
 *   <li>A4: hoja de 3 × 8 etiquetas de 70 × 37 mm, para imprimir en hojas autoadhesivas.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EtiquetaService {

    static final int MAXIMO_ETIQUETAS = 2000;
    /** Ancho de una barra del EAN-13 (módulo), en milímetros. */
    static final float MODULO_MM = 0.375f;
    private static final int A4_COLUMNAS = 3;
    private static final int A4_FILAS = 8;

    private final ProductoService productoService;
    private final EmpresaService empresaService;

    @Transactional(readOnly = true)
    public byte[] generar(EtiquetasRequest request) {
        int total = request.items().stream().mapToInt(EtiquetasRequest.Item::cantidad).sum();
        if (total > MAXIMO_ETIQUETAS) {
            throw new BusinessException("Son " + total + " etiquetas; el máximo por impresión es " + MAXIMO_ETIQUETAS);
        }
        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(
                request.items().stream().map(EtiquetasRequest.Item::varianteId).toList());
        String tienda = empresaService.buscarEntidad()
                .map(e -> StringUtils.hasText(e.getNombreComercial()) ? e.getNombreComercial() : e.getRazonSocial())
                .orElse("");
        List<ProductoVariante> etiquetas = request.items().stream()
                .flatMap(i -> Stream.generate(() -> variantes.get(i.varianteId())).limit(i.cantidad()))
                .toList();
        return request.formato() == EtiquetasRequest.Formato.A4 ? hojaA4(etiquetas, tienda) : rollo(etiquetas, tienda);
    }

    private static byte[] rollo(List<ProductoVariante> etiquetas, String tienda) {
        Rectangle pagina = new Rectangle(Utilities.millimetersToPoints(50), Utilities.millimetersToPoints(25));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(pagina, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(documento, salida);
        documento.open();
        PdfContentByte lienzo = writer.getDirectContent();
        for (int i = 0; i < etiquetas.size(); i++) {
            if (i > 0) {
                documento.newPage();
            }
            dibujar(lienzo, etiquetas.get(i), tienda, 0, 0, pagina.getWidth(), pagina.getHeight());
        }
        documento.close();
        return salida.toByteArray();
    }

    private static byte[] hojaA4(List<ProductoVariante> etiquetas, String tienda) {
        Rectangle pagina = new Rectangle(Utilities.millimetersToPoints(210), Utilities.millimetersToPoints(297));
        float ancho = Utilities.millimetersToPoints(70);
        float alto = Utilities.millimetersToPoints(37);
        float margenSuperior = (pagina.getHeight() - alto * A4_FILAS) / 2;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(pagina, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(documento, salida);
        documento.open();
        PdfContentByte lienzo = writer.getDirectContent();
        int porHoja = A4_COLUMNAS * A4_FILAS;
        for (int i = 0; i < etiquetas.size(); i++) {
            if (i > 0 && i % porHoja == 0) {
                documento.newPage();
            }
            int posicion = i % porHoja;
            float x = (posicion % A4_COLUMNAS) * ancho;
            float y = pagina.getHeight() - margenSuperior - (posicion / A4_COLUMNAS + 1) * alto;
            // Márgenes internos para que el corte de la hoja no toque el texto ni las barras.
            float m = Utilities.millimetersToPoints(4);
            dibujar(lienzo, etiquetas.get(i), tienda, x + m, y + m / 2, ancho - 2 * m, alto - m);
        }
        documento.close();
        return salida.toByteArray();
    }

    /** Una etiqueta en el rectángulo (x, y, ancho, alto), en puntos desde abajo a la izquierda. */
    private static void dibujar(PdfContentByte c, ProductoVariante v, String tienda, float x, float y, float ancho, float alto) {
        BaseFont normal = fuente(Font.NORMAL);
        BaseFont negrita = fuente(Font.BOLD);
        float margen = Math.min(ancho, alto) * 0.08f;
        float izquierda = x + margen;
        float derecha = x + ancho - margen;
        float arriba = y + alto - margen;

        float chico = alto * 0.085f;
        float mediano = alto * 0.12f;
        texto(c, normal, chico, recortar(tienda.toUpperCase(), normal, chico, ancho - 2 * margen), izquierda, arriba - chico, false);
        texto(c, negrita, mediano, recortar(v.getProducto().getNombre(), negrita, mediano, ancho - 2 * margen),
                izquierda, arriba - chico - mediano * 1.15f, false);
        String detalle = v.getColor().getNombre() + " · Talla " + v.getTalla().getCodigo();
        texto(c, normal, chico * 1.1f, detalle, izquierda, arriba - chico - mediano * 2.3f, false);
        texto(c, negrita, mediano * 1.15f, "S/ " + dinero(v.getPrecioEfectivo()), derecha, arriba - chico - mediano * 2.3f, true);
        BigDecimal mayor = v.getPrecioPorMayorEfectivo();
        if (mayor.compareTo(v.getPrecioEfectivo()) < 0) {
            texto(c, normal, chico * 0.95f, "x mayor S/ " + dinero(mayor), derecha, arriba - chico - mediano * 3.25f, true);
        }

        // Código de barras: ocupa la mitad inferior, con sus dígitos debajo.
        float altoDigitos = chico * 1.2f;
        float baseBarras = y + margen + altoDigitos;
        float altoBarras = (arriba - chico - mediano * 3.5f) - baseBarras;
        // Zona blanca de 11 módulos a cada lado (la que pide GS1 para EAN-13): sin ella algunos lectores fallan.
        BitMatrix barras = new EAN13Writer().encode(v.getCodigoBarras(), BarcodeFormat.EAN_13, 0, 1,
                Map.of(EncodeHintType.MARGIN, 11));
        // Ancho de barra fijo de 0.375 mm: 3 puntos exactos en una impresora térmica de 203 dpi (un
        // ancho que no cae en puntos enteros deforma las barras al imprimir y el lector falla).
        float modulo = Math.min(Utilities.millimetersToPoints(MODULO_MM), (ancho - 2 * margen) / barras.getWidth());
        float inicioBarras = x + (ancho - modulo * barras.getWidth()) / 2;
        c.setColorFill(Color.BLACK);
        for (int col = 0; col < barras.getWidth(); col++) {
            if (barras.get(col, 0)) {
                c.rectangle(inicioBarras + col * modulo, baseBarras, modulo, Math.max(altoBarras, alto * 0.25f));
            }
        }
        c.fill();
        float centro = x + ancho / 2;
        c.beginText();
        c.setFontAndSize(normal, chico * 1.05f);
        c.showTextAligned(PdfContentByte.ALIGN_CENTER, v.getCodigoBarras() + "   " + v.getSku(), centro, y + margen, 0);
        c.endText();
    }

    private static void texto(PdfContentByte c, BaseFont fuente, float tamano, String texto, float x, float y, boolean aLaDerecha) {
        c.beginText();
        c.setFontAndSize(fuente, tamano);
        c.showTextAligned(aLaDerecha ? PdfContentByte.ALIGN_RIGHT : PdfContentByte.ALIGN_LEFT, texto, x, y, 0);
        c.endText();
    }

    /** Recorta con "…" lo que no entra en el ancho. */
    private static String recortar(String texto, BaseFont fuente, float tamano, float ancho) {
        if (fuente.getWidthPoint(texto, tamano) <= ancho) {
            return texto;
        }
        String recortado = texto;
        while (recortado.length() > 1 && fuente.getWidthPoint(recortado + "…", tamano) > ancho) {
            recortado = recortado.substring(0, recortado.length() - 1);
        }
        return recortado.trim() + "…";
    }

    private static BaseFont fuente(int estilo) {
        return new Font(Font.HELVETICA, 10, estilo).getCalculatedBaseFont(false);
    }

    private static String dinero(BigDecimal monto) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(monto);
    }
}
