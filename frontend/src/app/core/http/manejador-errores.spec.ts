import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler } from '@angular/core';

import { ManejadorErrores } from './manejador-errores';

describe('ManejadorErrores', () => {
  it('no reporta otra vez los errores HTTP (ya los avisó el interceptor)', () => {
    const base = spyOn(ErrorHandler.prototype, 'handleError');
    const manejador = new ManejadorErrores();
    manejador.handleError(new HttpErrorResponse({ status: 400 }));
    manejador.handleError({ rejection: new HttpErrorResponse({ status: 409 }) });
    expect(base).not.toHaveBeenCalled();
    manejador.handleError(new Error('fallo de verdad'));
    expect(base).toHaveBeenCalledTimes(1);
  });
});
