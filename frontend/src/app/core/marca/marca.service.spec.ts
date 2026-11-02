import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';

import { erroresInterceptor } from '../http/errores.interceptor';
import { MarcaService } from './marca.service';

describe('MarcaService', () => {
  let servicio: MarcaService;
  let servidor: HttpTestingController;
  let avisos: MessageService;

  beforeEach(() => {
    localStorage.removeItem('allstore.marca');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([erroresInterceptor])), provideHttpClientTesting(), MessageService],
    });
    servicio = TestBed.inject(MarcaService);
    servidor = TestBed.inject(HttpTestingController);
    avisos = TestBed.inject(MessageService);
  });

  afterEach(() => localStorage.removeItem('allstore.marca'));

  it('toma el nombre comercial y separa la última palabra para el logo', async () => {
    const carga = servicio.cargar();
    servidor.expectOne('/api/publico/marca').flush({ nombre: 'KISSME JEANS', configurada: true, logoVersion: 123 });
    await carga;
    expect(servicio.nombre()).toBe('KISSME JEANS');
    expect(servicio.partes()).toEqual({ inicio: 'KISSME', fin: 'JEANS' });
    expect(servicio.inicial()).toBe('K');
    expect(servicio.logoUrl()).toBe('/api/publico/marca/logo?v=123');
    expect(JSON.parse(localStorage.getItem('allstore.marca')!).nombre).toBe('KISSME JEANS');
  });

  it('si la API falla, se queda con el nombre anterior y no muestra avisos', async () => {
    const add = spyOn(avisos, 'add');
    const carga = servicio.cargar();
    servidor.expectOne('/api/publico/marca').flush('', { status: 504, statusText: 'Gateway Timeout' });
    await carga;
    expect(servicio.nombre()).toBe('Mi tienda');
    expect(add).not.toHaveBeenCalled();
  });
});
