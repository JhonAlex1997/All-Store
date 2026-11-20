import { costoUnitario, proponerReparto, siguientePaso, validarCierre } from './produccion-reglas';

describe('producción: siguiente paso', () => {
  const etapas = (estados: string[], unidades: (number | undefined)[] = []) =>
    (['CORTE', 'CONFECCION', 'LAVADO', 'ACABADO'] as const).map((etapa, i) => ({ etapa, estado: estados[i] as never, unidades: unidades[i] }));

  it('va etapa por etapa y pasa las unidades que salieron de la anterior', () => {
    expect(siguientePaso({ estado: 'EN_PROCESO', planificadas: 100, etapas: etapas(['PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE']) }))
      .toEqual({ accion: 'iniciar', etapa: 'CORTE' });
    expect(siguientePaso({ estado: 'EN_PROCESO', planificadas: 100, etapas: etapas(['TERMINADA', 'EN_PROCESO', 'PENDIENTE', 'PENDIENTE'], [98]) }))
      .toEqual({ accion: 'terminar-etapa', etapa: 'CONFECCION', entraron: 98 });
    expect(siguientePaso({ estado: 'EN_PROCESO', planificadas: 100, etapas: etapas(['TERMINADA', 'TERMINADA', 'TERMINADA', 'TERMINADA'], [98, 97, 97, 95]) }))
      .toEqual({ accion: 'cerrar', salieron: 95 });
    expect(siguientePaso({ estado: 'TERMINADA', planificadas: 100, etapas: [] })).toEqual({ accion: 'ninguna' });
  });
});

describe('producción: reparto y cierre', () => {
  const tallas = [{ varianteId: 1, planificada: 10 }, { varianteId: 2, planificada: 20 }, { varianteId: 3, planificada: 30 }];

  it('si salió todo propone lo planificado; con merma reparte sin pasarse', () => {
    expect(proponerReparto(tallas, 60)).toEqual({ 1: 10, 2: 20, 3: 30 });
    const r = proponerReparto(tallas, 55);
    expect(r[1] + r[2] + r[3]).toBe(55);
    expect(r[1] <= 10 && r[2] <= 20 && r[3] <= 30).toBeTrue();
    expect(proponerReparto(tallas, 80)).toEqual({ 1: 10, 2: 20, 3: 30 });
  });

  it('valida enteros, mínimo una prenda y el tope del acabado', () => {
    expect(validarCierre({ 1: 10, 2: 20 }, 30)).toBeNull();
    expect(validarCierre({ 1: 0, 2: null }, 30)).toContain('cuántas');
    expect(validarCierre({ 1: 31 }, 30)).toContain('salieron 30');
    expect(validarCierre({ 1: 1.5 }, 30)).toContain('enteros');
  });

  it('costo unitario: solo insumos con costo más servicios, entre las prendas', () => {
    const orden = {
      insumos: [{ cantidad: 120.5, costo: 12.35 }, { cantidad: 300, costo: undefined }],
      etapas: [{ costoServicio: 450 }, { costoServicio: 300.5 }],
    };
    // 120.5 × 12.35 = 1488.175 → 1488.18; servicios 750.50; (2238.68) / 95 = 23.5651…
    expect(costoUnitario(orden, 95)).toEqual({ insumos: 1488.18, servicios: 750.5, unitario: 23.5651 });
    expect(costoUnitario(orden, 0).unitario).toBeNull();
  });
});
