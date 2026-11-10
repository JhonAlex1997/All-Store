import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./inventario-pagina').then((m) => m.InventarioPagina) },
];
