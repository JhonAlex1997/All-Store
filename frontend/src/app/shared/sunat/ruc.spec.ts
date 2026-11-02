import { esRucValido } from './ruc';

describe('esRucValido', () => {
  it('acepta RUC con dígito verificador correcto', () => {
    expect(esRucValido('20610224602')).toBeTrue();
    expect(esRucValido('20100070970')).toBeTrue();
  });

  it('rechaza dígito verificador, prefijo o longitud incorrectos', () => {
    expect(esRucValido('20610224603')).toBeFalse();
    expect(esRucValido('30610224602')).toBeFalse();
    expect(esRucValido('2061022460')).toBeFalse();
    expect(esRucValido('2061022460A')).toBeFalse();
  });
});
