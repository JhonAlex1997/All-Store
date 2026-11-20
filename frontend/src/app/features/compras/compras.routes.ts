import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./compras-pagina').then((m) => m.ComprasPagina) },
  { path: 'nueva', title: 'Registrar compra', loadComponent: () => import('./compra-nueva').then((m) => m.CompraNueva) },
  { path: ':id', title: 'Compra', loadComponent: () => import('./compra-detalle').then((m) => m.CompraDetalle) },
];
