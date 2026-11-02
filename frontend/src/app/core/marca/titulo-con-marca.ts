import { effect, inject, Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { MarcaService } from './marca.service';

/** Título de la pestaña: "Caja · KISSME JEANS". Las rutas solo declaran su parte ("Caja"). */
@Injectable()
export class TituloConMarca extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly marca = inject(MarcaService);
  private pagina: string | undefined;

  constructor() {
    super();
    // Si la marca llega después de navegar (respuesta de la API), se actualiza el título.
    effect(() => this.aplicar(this.marca.nombre()));
  }

  override updateTitle(estado: RouterStateSnapshot): void {
    this.pagina = this.buildTitle(estado);
    this.aplicar(this.marca.nombre());
  }

  private aplicar(marca: string): void {
    this.title.setTitle(this.pagina ? `${this.pagina} · ${marca}` : marca);
  }
}
