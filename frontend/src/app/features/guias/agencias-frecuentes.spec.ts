import { leerAgencias, recordarAgencia } from './agencias-frecuentes';

/** Almacenamiento en memoria para no tocar el localStorage real del navegador de pruebas. */
function memoria(inicial: Record<string, string> = {}): Storage {
  const datos = new Map(Object.entries(inicial));
  return {
    get length() { return datos.size; },
    clear: () => datos.clear(),
    getItem: (k) => datos.get(k) ?? null,
    key: (i) => [...datos.keys()][i] ?? null,
    removeItem: (k) => void datos.delete(k),
    setItem: (k, v) => void datos.set(k, v),
  };
}

describe('agencias frecuentes', () => {
  it('pone la última primero, sin repetir el RUC y con máximo 8', () => {
    const s = memoria();
    for (let i = 0; i < 10; i++) recordarAgencia({ ruc: `2010000000${i}`, razonSocial: `Agencia ${i}`, registroMtc: '' }, s);
    recordarAgencia({ ruc: '20100000005', razonSocial: ' Agencia 5 ', registroMtc: '' }, s);
    const lista = leerAgencias(s);
    expect(lista.length).toBe(8);
    expect(lista[0]).toEqual({ ruc: '20100000005', razonSocial: 'Agencia 5', registroMtc: '' });
    expect(lista.filter((a) => a.ruc === '20100000005').length).toBe(1);
  });

  it('ignora datos dañados y funciona sin almacenamiento', () => {
    expect(leerAgencias(memoria({ 'allstore.agencias': '{no es json' }))).toEqual([]);
    expect(leerAgencias(memoria({ 'allstore.agencias': '[{"ruc":1},{"ruc":"20100000001","razonSocial":"A"}]' })))
      .toEqual([{ ruc: '20100000001', razonSocial: 'A', registroMtc: '' }]);
    expect(leerAgencias(null)).toEqual([]);
    expect(recordarAgencia({ ruc: '1', razonSocial: 'B', registroMtc: '' }, null).length).toBe(1);
  });
});
