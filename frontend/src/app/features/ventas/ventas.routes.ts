import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./ventas-lista').then((m) => m.VentasLista) },
  { path: ':id', title: 'Comprobante', loadComponent: () => import('./venta-detalle').then((m) => m.VentaDetalle) },
];
