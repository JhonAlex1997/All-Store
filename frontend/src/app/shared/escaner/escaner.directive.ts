import { Directive, DestroyRef, inject, input, output } from '@angular/core';

/**
 * Lector de código de barras. Las pistolas USB escriben como un teclado muy rápido y terminan con Enter;
 * esta directiva junta esas teclas y emite el código completo.
 *
 * Uso en la caja (escucha toda la página, salvo que el foco esté en un campo de texto):
 *   <div asEscaner (codigo)="agregar($event)"></div>
 * Uso en un campo concreto:
 *   <input asEscaner [global]="false" (codigo)="buscar($event)" />
 */
@Directive({
  selector: '[asEscaner]',
  host: { '(keydown)': 'alTeclear($event)' },
})
export class EscanerDirective {
  /** true: escucha en todo el documento. false: solo en el elemento. */
  readonly global = input(true);
  /** Máximo de milisegundos entre teclas para considerar que es la pistola y no una persona. */
  readonly intervaloMs = input(50);
  readonly longitudMinima = input(4);

  readonly codigo = output<string>();

  private buffer = '';
  private ultima = 0;

  constructor() {
    const escuchar = (e: KeyboardEvent) => {
      if (this.global() && !esCampoDeTexto(e.target)) {
        this.procesar(e);
      }
    };
    document.addEventListener('keydown', escuchar, true);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('keydown', escuchar, true));
  }

  protected alTeclear(e: KeyboardEvent): void {
    if (!this.global()) {
      this.procesar(e);
    }
  }

  private procesar(e: KeyboardEvent): void {
    const ahora = performance.now();
    if (ahora - this.ultima > this.intervaloMs()) {
      this.buffer = '';
    }
    this.ultima = ahora;

    if (e.key === 'Enter') {
      if (this.buffer.length >= this.longitudMinima()) {
        e.preventDefault();
        this.codigo.emit(this.buffer);
      }
      this.buffer = '';
    } else if (e.key.length === 1) {
      this.buffer += e.key;
    }
  }
}

function esCampoDeTexto(objetivo: EventTarget | null): boolean {
  if (!(objetivo instanceof HTMLElement)) {
    return false;
  }
  return objetivo.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(objetivo.tagName);
}
