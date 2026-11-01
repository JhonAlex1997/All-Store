import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';

import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), MessageService, ConfirmationService],
    }).compileComponents();
  });

  it('crea la raíz con el router y el contenedor de avisos', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.querySelector('router-outlet')).toBeTruthy();
    expect(html.querySelector('p-toast')).toBeTruthy();
  });
});
