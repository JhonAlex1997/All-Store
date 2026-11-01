import { FechaLimaPipe } from './fecha-lima.pipe';
import { SolesPipe } from './soles.pipe';

describe('SolesPipe', () => {
  const pipe = new SolesPipe();

  it('formatea con dos decimales y separador de miles', () => {
    expect(pipe.transform(1234.5)).toBe('S/ 1,234.50');
    expect(pipe.transform('89.9')).toBe('S/ 89.90');
    expect(pipe.transform(0, false)).toBe('0.00');
  });

  it('deja vacío lo que no es número', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform('abc')).toBe('');
  });
});

describe('FechaLimaPipe', () => {
  const pipe = new FechaLimaPipe();

  it('muestra la hora de Lima sin importar la zona de la PC', () => {
    // 03:30 UTC del 2 de noviembre son las 22:30 del 1 de noviembre en Lima (UTC-5).
    expect(pipe.transform('2026-11-02T03:30:00Z', 'fecha-hora')).toBe('01/11/2026, 22:30');
    expect(pipe.transform('2026-11-02T03:30:00Z')).toBe('01/11/2026');
  });

  it('no mueve de día una fecha sin hora', () => {
    expect(pipe.transform('2026-11-01')).toBe('01/11/2026');
  });
});
