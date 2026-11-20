import { sugerirCodigo, unidadCorta } from './insumos-reglas';

describe('insumos', () => {
  it('sugiere un código válido para el backend (letras, números y guiones, máx. 30)', () => {
    expect(sugerirCodigo('Tela denim 12 oz')).toBe('TELA-DENIM-12');
    expect(sugerirCodigo('Botón metálico «Kiss Me»')).toBe('BOTON-METALI-KISS');
    expect(sugerirCodigo('  ')).toBe('');
    expect(/^[A-Z0-9-]*$/.test(sugerirCodigo('Ñandú & cía. #1'))).toBeTrue();
  });

  it('muestra la unidad corta y deja el código si no la conoce', () => {
    expect(unidadCorta('MTR')).toBe('m');
    expect(unidadCorta('ZZ')).toBe('ZZ');
    expect(unidadCorta()).toBe('');
  });
});
