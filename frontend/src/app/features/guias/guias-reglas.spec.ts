import { armarGuia, FormularioGuia, soloAlfanumerico } from './guias-reglas';

function base(): FormularioGuia {
  return {
    serieId: 7, motivo: 'TRASLADO_ESTABLECIMIENTOS', fechaTraslado: '2026-11-16', hoy: '2026-11-16',
    comprobanteId: null, ubigeoLlegada: '', direccionLlegada: '', entregarTodo: true,
    origenId: 1, destinoId: 2, items: [{ varianteId: 10, cantidad: 3 }, { varianteId: 11, cantidad: 0 }],
    pesoBrutoKg: 2.5, numeroBultos: null, modalidad: 'PRIVADO', vehiculoM1l: true,
    conductor: { tipoDocumento: 'DNI', numeroDocumento: '', nombres: '', apellidos: '', licencia: '' }, placa: '',
    transportista: { ruc: '', razonSocial: '', registroMtc: '' }, observacion: '',
  };
}

describe('armarGuia', () => {
  it('arma un traslado en auto propio sin conductor ni placa y quita las cantidades en cero', () => {
    const r = armarGuia(base());
    expect('request' in r).toBeTrue();
    if ('request' in r) {
      expect(r.request.items).toEqual([{ varianteId: 10, cantidad: 3 }]);
      expect(r.request.vehiculoM1l).toBeTrue();
      expect(r.request.conductor).toBeUndefined();
      expect(r.request.establecimientoDestinoId).toBe(2);
    }
  });

  it('rechaza fecha pasada, mismo local y peso vacío', () => {
    expect(armarGuia({ ...base(), fechaTraslado: '2026-11-15' })).toEqual({ error: jasmine.stringContaining('anterior a hoy') });
    expect(armarGuia({ ...base(), destinoId: 1 })).toEqual({ error: jasmine.stringContaining('distinto') });
    expect(armarGuia({ ...base(), pesoBrutoKg: null })).toEqual({ error: jasmine.stringContaining('peso') });
  });

  it('en transporte propio normaliza placa y licencia y exige sus formatos', () => {
    const f = {
      ...base(), vehiculoM1l: false, placa: 'abc-123',
      conductor: { tipoDocumento: 'DNI' as const, numeroDocumento: '45678912', nombres: 'Ana', apellidos: 'Ríos', licencia: 'q45678912' },
    };
    const r = armarGuia(f);
    expect('request' in r && r.request.placa).toBe('ABC123');
    expect('request' in r && r.request.conductor?.licencia).toBe('Q45678912');
    expect(armarGuia({ ...f, placa: 'AB1' })).toEqual({ error: jasmine.stringContaining('placa') });
    expect(armarGuia({ ...f, conductor: { ...f.conductor, numeroDocumento: '123' } })).toEqual({ error: jasmine.stringContaining('documento') });
  });

  it('en una venta exige comprobante, ubigeo y dirección; con «todo lo vendido» no envía prendas', () => {
    const venta: FormularioGuia = { ...base(), motivo: 'VENTA', comprobanteId: 15, ubigeoLlegada: '150101', direccionLlegada: ' Av. Lima 123 ' };
    const r = armarGuia(venta);
    expect('request' in r && r.request.items).toBeUndefined();
    expect('request' in r && r.request.llegada).toEqual({ ubigeo: '150101', direccion: 'Av. Lima 123' });
    expect('request' in r && r.request.establecimientoDestinoId).toBeUndefined();
    expect(armarGuia({ ...venta, ubigeoLlegada: '1501' })).toEqual({ error: jasmine.stringContaining('ubigeo') });
    expect(armarGuia({ ...venta, entregarTodo: false, items: [] })).toEqual({ error: jasmine.stringContaining('cuántas') });
  });

  it('en transporte público valida el RUC del transportista', () => {
    const f: FormularioGuia = { ...base(), modalidad: 'PUBLICO', transportista: { ruc: '20100000000', razonSocial: 'Trans SAC', registroMtc: '' } };
    expect(armarGuia(f)).toEqual({ error: jasmine.stringContaining('RUC') });
  });

  it('soloAlfanumerico', () => {
    expect(soloAlfanumerico(' a1b-2 c3 ')).toBe('A1B2C3');
  });
});
