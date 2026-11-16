import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./campanas-pagina').then((m) => m.CampanasPagina) },
];
