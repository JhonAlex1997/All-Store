import { billetesSugeridos, calcular, LineaCarrito, vuelto } from './calculo';

function linea(parcial: Partial<LineaCarrito>): LineaCarrito {
  return {
    varianteId: 1, productoId: 1, modelo: 'Short', codigoModelo: 'SH', sku: 'SH-1', talla: '28', color: 'Azul',
    precio: 59.9, precioPorMayor: 42, precioCampana: null, campana: null, stock: 10, cantidad: 1, ...parcial,
  };
}

describe('calcular (mismas reglas que VentaService)', () => {
  it('con menos prendas que el mínimo cobra el precio normal', () => {
    const t = calcular([linea({ cantidad: 2 })], 3, false);
    expect(t.porMayor).toBeFalse();
    expect(t.total).toBe(119.8);
    expect(t.faltanParaPorMayor).toBe(1);
  });

  it('desde el mínimo, sumando modelos distintos, va a precio por mayor', () => {
    const t = calcular([linea({ cantidad: 2 }), linea({ varianteId: 2, cantidad: 1 })], 3, false);
    expect(t.porMayor).toBeTrue();
    expect(t.porMayorPorCantidad).toBeTrue();
    expect(t.total).toBe(126);
  });

  it('el cajero puede marcar por mayor con una sola prenda', () => {
    const t = calcular([linea({ cantidad: 1 })], 3, true);
    expect(t.porMayor).toBeTrue();
    expect(t.porMayorPorCantidad).toBeFalse();
    expect(t.total).toBe(42);
  });

  it('la campaña se aplica solo si mejora el precio que corresponde', () => {
    const campana = linea({ precioCampana: 49.9, campana: 'Verano' });
    const normal = calcular([campana], 3, false);
    expect(normal.lineas[0].conCampana).toBeTrue();
    expect(normal.total).toBe(49.9);
    expect(normal.ahorroCampana).toBe(10);
    // Por mayor (42) es mejor que la campaña (49.90): gana el por mayor.
    const mayor = calcular([{ ...campana, cantidad: 3 }], 3, false);
    expect(mayor.lineas[0].conCampana).toBeFalse();
    expect(mayor.total).toBe(126);
  });

  it('suma en céntimos sin errores de redondeo', () => {
    const t = calcular([linea({ precio: 0.1, cantidad: 3 })], 99, false);
    expect(t.total).toBe(0.3);
  });
});

describe('cobro en efectivo', () => {
  it('calcula el vuelto y no lo deja negativo', () => {
    expect(vuelto(200, 126)).toBe(74);
    expect(vuelto(100, 126)).toBe(0);
  });

  it('sugiere el monto exacto y los billetes que lo cubren', () => {
    expect(billetesSugeridos(126)).toEqual([126, 130, 200]);
    expect(billetesSugeridos(42)).toEqual([42, 50, 100, 200]);
  });
});
