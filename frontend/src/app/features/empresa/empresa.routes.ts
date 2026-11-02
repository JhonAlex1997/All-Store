import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./empresa-pagina').then((m) => m.EmpresaPagina) },
];
