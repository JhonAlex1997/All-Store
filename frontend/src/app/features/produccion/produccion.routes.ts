import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./produccion-lista').then((m) => m.ProduccionLista) },
  { path: 'nueva', title: 'Nueva orden', loadComponent: () => import('./orden-nueva').then((m) => m.OrdenNueva) },
  { path: ':id', title: 'Orden de producción', loadComponent: () => import('./orden-detalle').then((m) => m.OrdenDetalle) },
];
