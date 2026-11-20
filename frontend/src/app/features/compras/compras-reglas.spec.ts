import { armarCompra, calcularTotales, FormularioCompra, LineaCompra, tieneDecimales } from './compras-reglas';

const tela: LineaCompra = { tipo: 'insumo', insumoId: 3, descripcion: 'Denim 12 oz', cantidad: 50.5, costoUnitario: 12.35, gravado: true };
const jean: LineaCompra = { tipo: 'prenda', varianteId: 7, descripcion: 'Jean Mom 28', cantidad: 10, costoUnitario: 45, gravado: true };
const flete: LineaCompra = { tipo: 'gasto', descripcion: 'Flete', cantidad: 1, costoUnitario: 30, gravado: false };

function base(): FormularioCompra {
  return {
    proveedorId: 1, establecimientoId: 2, tipoDocumento: 'FACTURA', serie: 'f001', numero: '000123', fechaEmision: '2026-11-10',
    periodo: '', hoy: '2026-11-19', observacion: '', actualizarCostos: false, lineas: [tela, jean, flete],
  };
}

describe('compras: totales', () => {
  it('en factura separa base e IGV por línea y deja lo no gravado aparte', () => {
    // 50.5 × 12.35 = 623.675 → 623.68; IGV 112.2624 → 112.26. 10 × 45 = 450; IGV 81.
    expect(calcularTotales('FACTURA', [tela, jean, flete])).toEqual({ baseGravada: 1073.68, igv: 193.26, noGravado: 30, total: 1296.94 });
  });

  it('en boleta no hay IGV: todo es no gravado', () => {
    expect(calcularTotales('BOLETA', [jean, flete])).toEqual({ baseGravada: 0, igv: 0, noGravado: 480, total: 480 });
  });
});

describe('compras: armarCompra', () => {
  it('normaliza serie y número y arma cada tipo de línea', () => {
    const r = armarCompra(base());
    expect('request' in r).toBeTrue();
    if ('request' in r) {
      expect(r.request.serie).toBe('F001');
      expect(r.request.numero).toBe('123');
      expect(r.request.periodo).toBeUndefined();
      expect(r.request.items.map((i) => [i.insumoId, i.varianteId, i.descripcion])).toEqual([
        [3, undefined, undefined], [undefined, 7, undefined], [undefined, undefined, 'Flete'],
      ]);
    }
  });

  it('rechaza fecha futura, periodo fuera de rango, prendas con decimales y líneas repetidas', () => {
    expect(armarCompra({ ...base(), fechaEmision: '2026-11-20' })).toEqual({ error: jasmine.stringContaining('futura') });
    expect(armarCompra({ ...base(), periodo: '2026-10' })).toEqual({ error: jasmine.stringContaining('periodo') });
    expect('request' in armarCompra({ ...base(), periodo: '2027-11' })).toBeTrue();
    expect(armarCompra({ ...base(), periodo: '2027-12' })).toEqual({ error: jasmine.stringContaining('12 meses') });
    expect(armarCompra({ ...base(), lineas: [{ ...jean, cantidad: 1.5 }] })).toEqual({ error: jasmine.stringContaining('enteras') });
    expect(armarCompra({ ...base(), lineas: [jean, { ...jean }] })).toEqual({ error: jasmine.stringContaining('dos veces') });
    expect(armarCompra({ ...base(), lineas: [{ ...flete, descripcion: ' ' }] })).toEqual({ error: jasmine.stringContaining('describe') });
    expect(armarCompra({ ...base(), lineas: [{ ...flete, costoUnitario: 0 }] })).toEqual({ error: jasmine.stringContaining('mayor a cero') });
  });

  it('tieneDecimales tolera el punto flotante', () => {
    expect(tieneDecimales(12.3456, 4)).toBeTrue();
    expect(tieneDecimales(0.1 + 0.2, 2)).toBeTrue();
    expect(tieneDecimales(1.23456, 4)).toBeFalse();
  });
});
