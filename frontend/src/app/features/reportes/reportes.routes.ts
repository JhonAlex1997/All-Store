import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./reportes-pagina').then((m) => m.ReportesPagina) },
];
