import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./caja-pagina').then((m) => m.CajaPagina) },
];
