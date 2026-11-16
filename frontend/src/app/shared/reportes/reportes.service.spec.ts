import { completarDias, VentaDiaFila } from './reportes.service';

describe('completarDias', () => {
  const fila = (fecha: string, neto: number): VentaDiaFila => ({ fecha, comprobantes: 1, prendas: 1, ventas: neto, notasCredito: 0, neto });

  it('rellena con cero los días sin ventas y respeta el orden', () => {
    const r = completarDias([fila('2026-11-01', 195), fila('2026-11-04', 448)], '2026-11-01', '2026-11-05');
    expect(r.map((f) => f.fecha)).toEqual(['2026-11-01', '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05']);
    expect(r.map((f) => f.neto)).toEqual([195, 0, 0, 448, 0]);
  });

  it('cruza fin de mes y de año', () => {
    const r = completarDias([], '2026-12-30', '2027-01-02');
    expect(r.map((f) => f.fecha)).toEqual(['2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02']);
  });

  it('devuelve vacío si el rango está invertido', () => {
    expect(completarDias([], '2026-11-05', '2026-11-01')).toEqual([]);
  });
});
