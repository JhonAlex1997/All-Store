import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * Tema de AllStore sobre Aura (PrimeNG): azul denim como color principal, como en los mockups.
 * El cobre se usa como acento en el logo y en los detalles (ver styles.scss).
 */
export const AllStorePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eef3fa',
      100: '#d5e1f1',
      200: '#adc3e3',
      300: '#7f9fcf',
      400: '#527ab5',
      500: '#2b5288',
      600: '#24467a',
      700: '#1f3b63',
      800: '#182f50',
      900: '#13253f',
      950: '#0c182a',
    },
    focusRing: { width: '2px', style: 'solid', color: '{primary.400}', offset: '1px' },
  },
});
