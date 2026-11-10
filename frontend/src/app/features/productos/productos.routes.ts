import { Routes } from '@angular/router';

import { rolGuard } from '../../core/auth/guards';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./productos-lista').then((m) => m.ProductosLista) },
  {
    path: 'catalogos',
    title: 'Tallas, colores y categorías',
    canActivate: [rolGuard],
    data: { roles: ['ADMIN', 'ALMACENERO'] },
    loadComponent: () => import('./catalogos').then((m) => m.Catalogos),
  },
  { path: ':id', title: 'Modelo', loadComponent: () => import('./producto-detalle').then((m) => m.ProductoDetalle) },
];
