package com.allstore.api.empresa.service;

import java.awt.image.BufferedImage;

/**
 * Ajustes de color del logo, sin dependencias: quitar un fondo blanco y preparar la versión para fondos
 * oscuros (el menú y el login son negros).
 */
final class TratamientoLogo {

    /** Distancia al blanco por debajo de la cual un píxel se considera fondo (ruido del JPG). */
    private static final int UMBRAL_FONDO = 6;
    /**
     * Croma (máximo - mínimo de los canales) por debajo del cual un color es "neutro" (negro, gris): en fondo
     * oscuro pasa a blanco. Se usa el croma y no la saturación porque un negro casi puro, como (2, 2, 0),
     * tendría saturación alta sin ser un color.
     */
    private static final int CROMA_NEUTRO = 24;

    private TratamientoLogo() {
    }

    /**
     * Si la imagen no tiene transparencia y su borde es blanco (el típico JPG), convierte el blanco en
     * transparente conservando el color de lo demás ("color a alfa"): sobre blanco se ve idéntica, y
     * sobre otro fondo no queda un recuadro blanco.
     */
    static BufferedImage quitarFondoBlanco(BufferedImage img) {
        if (tieneTransparencia(img) || !bordeBlanco(img)) {
            return img;
        }
        BufferedImage salida = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int alfa = Math.max(255 - r, Math.max(255 - g, 255 - b));
                if (alfa < UMBRAL_FONDO) {
                    salida.setRGB(x, y, 0);
                    continue;
                }
                double f = alfa / 255.0;
                double blanco = 255 * (1 - f);
                salida.setRGB(x, y, argb(alfa, sinBlanco(r, blanco, f), sinBlanco(g, blanco, f), sinBlanco(b, blanco, f)));
            }
        }
        return salida;
    }

    /**
     * Versión para fondo oscuro: lo negro o gris (el texto) pasa a blanco y los colores se muestran como
     * se ven sobre blanco, con opacidad completa. En el logo de Kiss Me: "KISS ME JEANS" en blanco y el
     * "Km" en su rosado.
     */
    static BufferedImage paraFondoOscuro(BufferedImage img) {
        int ancho = img.getWidth(), alto = img.getHeight();
        // Un logo pasado por "color a alfa" (o un JPG) queda con el texto y los colores algo transparentes:
        // sobre negro se verían grises y apagados. Se normaliza la opacidad de cada grupo (neutros y colores)
        // con la que tiene la mayoría de sus píxeles (percentil 90), para que se vean llenos.
        int[] histNeutro = new int[256];
        int[] histColor = new int[256];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int p = img.getRGB(x, y);
                int alfa = p >>> 24;
                if (alfa > 0) {
                    (neutro(p) ? histNeutro : histColor)[alfa]++;
                }
            }
        }
        int referenciaNeutro = percentil(histNeutro, 0.9);
        int referenciaColor = percentil(histColor, 0.9);
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int p = img.getRGB(x, y);
                int alfa = p >>> 24;
                if (alfa == 0) {
                    continue;
                }
                if (neutro(p)) {
                    salida.setRGB(x, y, argb(escalar(alfa, referenciaNeutro), 255, 255, 255));
                } else {
                    // El color como se ve sobre blanco con la opacidad de referencia: el rosado del logo.
                    double f = Math.min(1.0, alfa / (double) referenciaColor) * referenciaColor / 255.0;
                    int r = sobreBlanco((p >> 16) & 0xFF, f), g = sobreBlanco((p >> 8) & 0xFF, f), b = sobreBlanco(p & 0xFF, f);
                    salida.setRGB(x, y, argb(escalar(alfa, referenciaColor), r, g, b));
                }
            }
        }
        return salida;
    }

    /** Valor del histograma de opacidades por debajo del cual queda la fracción indicada de píxeles. */
    private static int percentil(int[] histograma, double fraccion) {
        long total = 0;
        for (int v : histograma) {
            total += v;
        }
        if (total == 0) {
            return 255;
        }
        long acumulado = 0;
        for (int i = 0; i < histograma.length; i++) {
            acumulado += histograma[i];
            if (acumulado >= total * fraccion) {
                return Math.max(i, 1);
            }
        }
        return 255;
    }

    private static int escalar(int alfa, int referencia) {
        return Math.min(255, Math.round(alfa * 255f / referencia));
    }

    private static boolean neutro(int p) {
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max - min < CROMA_NEUTRO;
    }

    private static boolean tieneTransparencia(BufferedImage img) {
        if (!img.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                if ((img.getRGB(x, y) >>> 24) < 250) {
                    return true;
                }
            }
        }
        return false;
    }

    /** El borde (1 px alrededor) es casi todo blanco: es un fondo, no parte del dibujo. */
    private static boolean bordeBlanco(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight(), total = 0, blancos = 0;
        for (int x = 0; x < w; x++) {
            total += 2;
            blancos += (casiBlanco(img.getRGB(x, 0)) ? 1 : 0) + (casiBlanco(img.getRGB(x, h - 1)) ? 1 : 0);
        }
        for (int y = 0; y < h; y++) {
            total += 2;
            blancos += (casiBlanco(img.getRGB(0, y)) ? 1 : 0) + (casiBlanco(img.getRGB(w - 1, y)) ? 1 : 0);
        }
        return blancos >= total * 0.95;
    }

    private static boolean casiBlanco(int p) {
        return ((p >> 16) & 0xFF) > 240 && ((p >> 8) & 0xFF) > 240 && (p & 0xFF) > 240;
    }

    private static int sinBlanco(int canal, double blanco, double f) {
        return limitar((int) Math.round((canal - blanco) / f));
    }

    private static int sobreBlanco(int canal, double f) {
        return limitar((int) Math.round(canal * f + 255 * (1 - f)));
    }

    private static int limitar(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
