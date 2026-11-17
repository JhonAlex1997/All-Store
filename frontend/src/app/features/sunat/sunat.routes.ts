import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./sunat-pagina').then((m) => m.SunatPagina) },
];
