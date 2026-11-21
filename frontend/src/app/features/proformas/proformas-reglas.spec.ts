import { estadoDe, parametrosDe } from './proformas-reglas';

describe('proformas-reglas', () => {
  it('traduce cada filtro a los parámetros de la API', () => {
    expect(parametrosDe('VIGENTES')).toEqual({ vigencia: 'VIGENTE' });
    expect(parametrosDe('VENCIDAS')).toEqual({ vigencia: 'VENCIDA' });
    expect(parametrosDe('VENDIDAS')).toEqual({ estado: 'VENDIDA' });
    expect(parametrosDe('ANULADAS')).toEqual({ estado: 'ANULADA' });
    expect(parametrosDe('TODAS')).toEqual({});
  });

  it('muestra la vigencia con los días que quedan', () => {
    const vigente = estadoDe({ estado: 'PENDIENTE', vencida: false, diasVigencia: 5, vigenteHasta: '2026-11-28' });
    expect(vigente.etiqueta).toBe('Vigente');
    expect(vigente.detalle).toBe('hasta el 28/11/2026 (5 días)');
    expect(estadoDe({ estado: 'PENDIENTE', vencida: false, diasVigencia: 0, vigenteHasta: '2026-11-21' }).etiqueta).toBe('Vence hoy');
    expect(estadoDe({ estado: 'PENDIENTE', vencida: false, diasVigencia: 1, vigenteHasta: '2026-11-22' }).severidad).toBe('warn');
  });

  it('distingue vencida, vendida y anulada', () => {
    expect(estadoDe({ estado: 'PENDIENTE', vencida: true, vigenteHasta: '2026-11-10' }).detalle).toBe('venció el 10/11/2026');
    expect(estadoDe({ estado: 'VENDIDA', comprobante: 'B001-00000015' }).detalle).toBe('con B001-00000015');
    expect(estadoDe({ estado: 'ANULADA' }).etiqueta).toBe('Anulada');
  });
});
