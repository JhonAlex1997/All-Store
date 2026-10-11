package com.allstore.api.impresion;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PDF del comprobante, sin base de datos: se lee su texto y se decodifica el QR generado. */
class ComprobantePdfTest {

    private static final String QR = "20000000001|03|B001|15|25.90|169.80|2026-10-10|1|45678912|abc123=|";

    private final ComprobantePdf generador = new ComprobantePdf();

    @Test
    void ticketTraeLoQueExigeSunat() throws Exception {
        byte[] pdf = generador.generar(boleta(null), FormatoImpresion.TICKET);
        String texto = texto(pdf);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(texto).contains("BOLETA DE VENTA ELECTRÓNICA", "B001-00000015", "RUC 20000000001",
                "Juan Pérez Rojas", "169.80", "SON: CIENTO SESENTA Y NUEVE CON 80/100 SOLES",
                "Hash: abc123=", "Representación impresa");
        // Una sola página de 80 mm de ancho: la impresora térmica corta donde termina.
        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isEqualTo(1);
        assertThat(lector.getPageSize(1).getWidth()).isCloseTo(226.77f, org.assertj.core.data.Offset.offset(0.1f));
    }

    @Test
    void a4ConMarcaDeAguaCuandoCorresponde() throws Exception {
        String texto = texto(generador.generar(boleta("ANULADO"), FormatoImpresion.A4));

        assertThat(texto).contains("R.U.C. N° 20000000001", "IMPORTE TOTAL S/", "ANULADO");
    }

    @Test
    void elQrContieneExactamenteElTextoDeSunat() throws Exception {
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(CodigoQr.png(QR, 300)));
        int[] pixeles = imagen.getRGB(0, 0, imagen.getWidth(), imagen.getHeight(), null, 0, imagen.getWidth());
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(imagen.getWidth(), imagen.getHeight(), pixeles)));

        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(QR);
    }

    @Test
    void montosConFormatoPeruano() {
        assertThat(ComprobantePdf.dinero(new BigDecimal("1234.5"))).isEqualTo("1,234.50");
    }

    private static DatosImpresion boleta(String marcaDeAgua) {
        return new DatosImpresion(
                "BOLETA DE VENTA ELECTRÓNICA", "B001-00000015", marcaDeAgua,
                new DatosImpresion.Emisor("20000000001", "EMPRESA DEMO S.A.C.", "AllStore Jeans", "Jr. Gamarra 100",
                        "014567890", null),
                "Tienda principal", "Jr. Gamarra 100",
                OffsetDateTime.of(2026, 10, 10, 15, 30, 0, 0, ZoneOffset.ofHours(-5)), "cajero",
                new DatosImpresion.Cliente("DNI", "45678912", "Juan Pérez Rojas", null),
                null,
                List.of(new DatosImpresion.Linea(2, "UND", "MOM01-AZU-28", "Jean Mom Fit Clásico - Azul talla 28",
                        new BigDecimal("89.90"), new BigDecimal("10.00"), new BigDecimal("169.80"))),
                new BigDecimal("143.90"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"),
                new BigDecimal("25.90"), new BigDecimal("169.80"),
                "CIENTO SESENTA Y NUEVE CON 80/100 SOLES",
                List.of(new DatosImpresion.Pago("Efectivo", new BigDecimal("169.80"), null)),
                new BigDecimal("30.20"), QR, "abc123=",
                "Representación impresa de la Boleta de Venta Electrónica. Consulte su validez en www.sunat.gob.pe.",
                null);
    }

    private static String texto(byte[] pdf) throws Exception {
        return new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
    }
}
