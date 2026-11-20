import { mesLima, nombreDeCabecera } from './contabilidad.service';

describe('contabilidad: utilidades', () => {
  it('toma el nombre del archivo de Content-Disposition sin rutas ni caracteres raros', () => {
    expect(nombreDeCabecera('attachment; filename="LE2061022460220261100140400021112.zip"')).toBe('LE2061022460220261100140400021112.zip');
    expect(nombreDeCabecera("attachment; filename*=UTF-8''registro%20ventas.txt")).toBe('registro ventas.txt');
    expect(nombreDeCabecera('attachment; filename="../../x.txt"')).toBe('x.txt');
    expect(nombreDeCabecera(null)).toBeNull();
    expect(nombreDeCabecera('attachment')).toBeNull();
  });

  it('mesLima devuelve AAAA-MM y cruza el año', () => {
    expect(/^\d{4}-(0[1-9]|1[0-2])$/.test(mesLima())).toBeTrue();
    const [a, m] = mesLima().split('-').map(Number);
    const anterior = mesLima(-m);
    expect(anterior).toBe(`${a - 1}-12`);
  });
});
