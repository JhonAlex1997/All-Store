import { FormControl } from '@angular/forms';

import { passwordTemporal, politicaPassword } from './politica-password';

describe('política de contraseñas', () => {
  const validar = (valor: string) => politicaPassword(new FormControl(valor, { nonNullable: true }));

  it('exige 8 caracteres con al menos una letra y un número', () => {
    expect(validar('abc12')).toEqual({ corta: true });
    expect(validar('abcdefgh')).toEqual({ patron: true });
    expect(validar('abcdefg1')).toBeNull();
  });

  it('las temporales cumplen la política y no repiten', () => {
    const generadas = new Set<string>();
    for (let i = 0; i < 50; i++) {
      const p = passwordTemporal();
      expect(validar(p)).toBeNull();
      expect(p).not.toMatch(/[01ilo]/);
      generadas.add(p);
    }
    expect(generadas.size).toBeGreaterThan(45);
  });
});
