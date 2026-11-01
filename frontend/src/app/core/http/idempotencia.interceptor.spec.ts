import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CLAVE_IDEMPOTENCIA, idempotenciaInterceptor, nuevaClave } from './idempotencia.interceptor';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('idempotenciaInterceptor', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([idempotenciaInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
  });

  afterEach(() => servidor.verify());

  it('pone una clave distinta en cada operación que modifica datos', () => {
    http.post('/api/ventas', {}).subscribe();
    http.post('/api/ventas', {}).subscribe();
    const [a, b] = servidor.match('/api/ventas');
    const ka = a.request.headers.get('Idempotency-Key');
    const kb = b.request.headers.get('Idempotency-Key');
    expect(ka).toMatch(UUID);
    expect(kb).toMatch(UUID);
    expect(ka).not.toEqual(kb);
    a.flush({});
    b.flush({});
  });

  it('no la pone en consultas ni fuera de la API', () => {
    http.get('/api/ventas').subscribe();
    http.post('/otro', {}).subscribe();
    expect(servidor.expectOne('/api/ventas').request.headers.has('Idempotency-Key')).toBeFalse();
    expect(servidor.expectOne('/otro').request.headers.has('Idempotency-Key')).toBeFalse();
  });

  it('usa la clave fija de la pantalla para reintentar la misma operación', () => {
    const context = new HttpContext().set(CLAVE_IDEMPOTENCIA, 'reintento-123');
    http.post('/api/caja/movimientos', {}, { context }).subscribe();
    expect(servidor.expectOne('/api/caja/movimientos').request.headers.get('Idempotency-Key')).toBe('reintento-123');
  });

  it('genera un UUID v4 válido aunque no exista crypto.randomUUID (http por IP)', () => {
    const original = crypto.randomUUID;
    try {
      (crypto as { randomUUID?: unknown }).randomUUID = undefined;
      expect(nuevaClave()).toMatch(UUID);
    } finally {
      (crypto as { randomUUID?: unknown }).randomUUID = original;
    }
  });
});
