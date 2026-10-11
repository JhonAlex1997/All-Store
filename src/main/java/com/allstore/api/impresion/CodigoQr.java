package com.allstore.api.impresion;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** Genera la imagen del código QR que SUNAT exige en la representación impresa. */
public final class CodigoQr {

    private static final int NEGRO = 0xFF000000;
    private static final int BLANCO = 0xFFFFFFFF;

    private CodigoQr() {
    }

    /** PNG cuadrado de {@code pixeles} de lado, con corrección de errores media (legible aunque se arrugue el ticket). */
    public static byte[] png(String texto, int pixeles) {
        try {
            Map<EncodeHintType, Object> opciones = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matriz = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, pixeles, pixeles, opciones);
            BufferedImage imagen = new BufferedImage(matriz.getWidth(), matriz.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matriz.getWidth(); x++) {
                for (int y = 0; y < matriz.getHeight(); y++) {
                    imagen.setRGB(x, y, matriz.get(x, y) ? NEGRO : BLANCO);
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }
    }
}
