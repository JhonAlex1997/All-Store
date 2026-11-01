import { HttpErrorResponse } from '@angular/common/http';

import { leerErrorApi } from './error-api';

describe('leerErrorApi', () => {
  it('usa el mensaje del backend cuando viene su JSON', async () => {
    const e = new HttpErrorResponse({
      status: 404, url: '/api/ventas/9/pdf',
      error: { status: 404, message: 'Comprobante no encontrado: 9', solicitud: 'abc123' },
    });
    const r = await leerErrorApi(e);
    expect(r.message).toBe('Comprobante no encontrado: 9');
    expect(r.solicitud).toBe('abc123');
  });

  it('lee el JSON aunque llegue como Blob (descargas)', async () => {
    const blob = new Blob([JSON.stringify({ status: 409, message: 'Caja cerrada' })], { type: 'application/json' });
    const r = await leerErrorApi(new HttpErrorResponse({ status: 409, url: '/api/caja', error: blob }));
    expect(r.message).toBe('Caja cerrada');
  });

  it('avisa que no llegó al backend si el 404 o 5xx de /api no trae su JSON', async () => {
    for (const status of [404, 500, 504]) {
      const r = await leerErrorApi(new HttpErrorResponse({ status, url: 'http://localhost:4200/api/auth/login', error: '' }));
      expect(r.message).toContain('npm start');
    }
  });
});
