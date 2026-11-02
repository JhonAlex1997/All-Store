import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * Tema sobre Aura (PrimeNG) con los colores de la marca Kiss Me: botones negros como el texto del logo
 * y el rosado del "Km" en selecciones, foco y detalles. Los mismos tonos están en styles.scss (--as-*).
 */
export const AllStorePreset = definePreset(Aura, {
  semantic: {
    // Escala rosada: la usan los resaltados (fila elegida, opción activa) y el anillo de foco.
    primary: {
      50: '#fdf4f6',
      100: '#fbe7ec',
      200: '#f7d0d9',
      300: '#f4c4cd',
      400: '#e48aa0',
      500: '#d66b87',
      600: '#b84d6c',
      700: '#963c57',
      800: '#7a3348',
      900: '#652d3e',
      950: '#3b141f',
    },
    focusRing: { width: '2px', style: 'solid', color: '{primary.400}', offset: '1px' },
    colorScheme: {
      light: {
        // Botones y elementos principales en negro, como "KISS ME" del logo.
        primary: {
          color: '#1d1d1b',
          contrastColor: '#ffffff',
          hoverColor: '#3a3a37',
          activeColor: '#000000',
        },
        highlight: {
          background: '{primary.50}',
          focusBackground: '{primary.100}',
          color: '{primary.700}',
          focusColor: '{primary.800}',
        },
      },
    },
  },
});
