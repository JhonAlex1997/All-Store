import { HttpErrorResponse } from '@angular/common/http';

import { esEdicionConcurrente, ifMatch } from './version';

describe('version (bloqueo optimista)', () => {
  it('arma la cabecera If-Match solo si hay versión', () => {
    expect(ifMatch(0)).toBe('0');
    expect(ifMatch(7)).toBe('7');
    expect(ifMatch(undefined)).toBeUndefined();
    expect(ifMatch(null)).toBeUndefined();
  });

  it('reconoce el 412 de edición concurrente y nada más', () => {
    expect(esEdicionConcurrente(new HttpErrorResponse({ status: 412 }))).toBeTrue();
    expect(esEdicionConcurrente(new HttpErrorResponse({ status: 409 }))).toBeFalse();
    expect(esEdicionConcurrente(new Error('x'))).toBeFalse();
  });
});
