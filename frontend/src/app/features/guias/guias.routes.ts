import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./guias-lista').then((m) => m.GuiasLista) },
  { path: 'nueva', title: 'Nueva guía', loadComponent: () => import('./guia-nueva').then((m) => m.GuiaNueva) },
  { path: ':id', title: 'Guía de remisión', loadComponent: () => import('./guia-detalle').then((m) => m.GuiaDetalle) },
];
