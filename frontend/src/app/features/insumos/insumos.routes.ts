import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./insumos-pagina').then((m) => m.InsumosPagina) },
];
