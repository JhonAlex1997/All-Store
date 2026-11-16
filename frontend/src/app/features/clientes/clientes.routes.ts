import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./clientes-pagina').then((m) => m.ClientesPagina) },
];
