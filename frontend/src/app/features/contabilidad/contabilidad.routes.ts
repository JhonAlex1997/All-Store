import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./contabilidad-pagina').then((m) => m.ContabilidadPagina) },
];
