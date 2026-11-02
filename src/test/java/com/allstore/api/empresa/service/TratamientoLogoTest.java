package com.allstore.api.empresa.service;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/** Quitar el fondo blanco de un logo y prepararlo para el menú negro, con un logo como el de Kiss Me. */
class TratamientoLogoTest {

    private static final Color ROSADO = new Color(244, 196, 205);
    private static final Color NEGRO = new Color(29, 29, 27);

    @Test
    void elFondoBlancoSeVuelveTransparenteYLoDemasSeVeIgualSobreBlanco() {
        BufferedImage limpio = TratamientoLogo.quitarFondoBlanco(logoJpg());

        assertThat(alfa(limpio, 0, 0)).as("fondo").isZero();
        assertThat(alfa(limpio, 15, 5)).as("texto negro opaco").isGreaterThan(220);
        // El rosado queda semitransparente, pero sobre blanco recupera exactamente su color.
        int p = limpio.getRGB(15, 15);
        assertThat(sobreBlanco(p)).isEqualTo(new int[]{244, 196, 205});
    }

    @Test
    void enFondoOscuroElTextoPasaABlancoYElRosadoSeMantieneLleno() {
        BufferedImage oscuro = TratamientoLogo.paraFondoOscuro(TratamientoLogo.quitarFondoBlanco(logoJpg()));

        int texto = oscuro.getRGB(15, 5);
        assertThat(texto & 0xFFFFFF).as("texto en blanco").isEqualTo(0xFFFFFF);
        int rosado = oscuro.getRGB(15, 15);
        assertThat(rosado >>> 24).as("rosado opaco").isEqualTo(255);
        assertThat(new Color(rosado & 0xFFFFFF)).isEqualTo(ROSADO);
        assertThat(alfa(oscuro, 0, 0)).as("fondo sigue transparente").isZero();
    }

    @Test
    void unLogoConFondoDeColorNoSeToca() {
        BufferedImage azul = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        var g = azul.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        assertThat(TratamientoLogo.quitarFondoBlanco(azul)).isSameAs(azul);
    }

    /** 30x20 blanco: franja negra arriba (el texto) y franja rosada abajo (el "Km"). */
    private static BufferedImage logoJpg() {
        BufferedImage img = new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 30, 20);
        g.setColor(NEGRO);
        g.fillRect(5, 3, 20, 5);
        g.setColor(ROSADO);
        g.fillRect(5, 12, 20, 5);
        g.dispose();
        return img;
    }

    private static int alfa(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) >>> 24;
    }

    private static int[] sobreBlanco(int p) {
        double f = (p >>> 24) / 255.0;
        return new int[]{
                (int) Math.round(((p >> 16) & 0xFF) * f + 255 * (1 - f)),
                (int) Math.round(((p >> 8) & 0xFF) * f + 255 * (1 - f)),
                (int) Math.round((p & 0xFF) * f + 255 * (1 - f))};
    }
}
