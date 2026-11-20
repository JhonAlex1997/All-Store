import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./monitoreo-pagina').then((m) => m.MonitoreoPagina) },
];
