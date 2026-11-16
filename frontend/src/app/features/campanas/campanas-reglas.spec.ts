import { aplicarDescuento, describirDescuento, estadoCampana } from './campanas-reglas';

describe('reglas de campañas', () => {
  it('aplica porcentaje con redondeo a céntimos, como el backend', () => {
    expect(aplicarDescuento(89.9, 'PORCENTAJE', 15)).toBe(76.42); // 76.415 → 76.42
    expect(aplicarDescuento(100, 'PORCENTAJE', 20)).toBe(80);
  });

  it('descuento en soles nunca baja de S/ 0.01', () => {
    expect(aplicarDescuento(89.9, 'MONTO', 10)).toBe(79.9);
    expect(aplicarDescuento(5, 'MONTO', 10)).toBe(0.01);
  });

  it('precio fijo nunca sube el precio normal', () => {
    expect(aplicarDescuento(89.9, 'PRECIO_FIJO', 59.9)).toBe(59.9);
    expect(aplicarDescuento(49.9, 'PRECIO_FIJO', 59.9)).toBe(49.9);
  });

  it('describe el descuento', () => {
    expect(describirDescuento('PORCENTAJE', 20)).toBe('20 % de descuento');
    expect(describirDescuento('MONTO', 10)).toBe('S/ 10.00 menos por prenda');
    expect(describirDescuento('PRECIO_FIJO', 59.9)).toBe('Precio fijo S/ 59.90');
  });

  it('calcula el estado por fechas', () => {
    const hoy = '2026-11-16';
    expect(estadoCampana({ activo: true, fechaInicio: '2026-11-10', fechaFin: '2026-11-16' }, hoy)).toBe('VIGENTE');
    expect(estadoCampana({ activo: true, fechaInicio: '2026-11-17', fechaFin: '2026-11-30' }, hoy)).toBe('PROXIMA');
    expect(estadoCampana({ activo: true, fechaInicio: '2026-11-01', fechaFin: '2026-11-15' }, hoy)).toBe('TERMINADA');
    expect(estadoCampana({ activo: false, fechaInicio: '2026-11-10', fechaFin: '2026-11-20' }, hoy)).toBe('PAUSADA');
    expect(estadoCampana({ activo: false, fechaInicio: '2026-11-01', fechaFin: '2026-11-05' }, hoy)).toBe('TERMINADA');
  });
});
