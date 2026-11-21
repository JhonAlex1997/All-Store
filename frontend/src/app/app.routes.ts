import { Route, Routes } from '@angular/router';

import { autenticadoGuard, invitadoGuard, rolGuard } from './core/auth/guards';
import { Shell } from './core/layout/shell';
import { MENU, OpcionMenu } from './core/layout/menu';

const aviso = () => import('./paginas/aviso/aviso').then((m) => m.Aviso);

/**
 * Módulos aún no construidos: muestran "en construcción" con los roles del menú.
 * Cuando se construya uno, se agrega en MODULOS con su loadChildren y deja de usar este marcador.
 */
const MODULOS: Partial<Record<string, Route['loadChildren']>> = {
  caja: () => import('./features/caja/caja.routes').then((m) => m.routes),
  productos: () => import('./features/productos/productos.routes').then((m) => m.routes),
  inventario: () => import('./features/inventario/inventario.routes').then((m) => m.routes),
  usuarios: () => import('./features/usuarios/usuarios.routes').then((m) => m.routes),
  ventas: () => import('./features/ventas/ventas.routes').then((m) => m.routes),
  reportes: () => import('./features/reportes/reportes.routes').then((m) => m.routes),
  clientes: () => import('./features/clientes/clientes.routes').then((m) => m.routes),
  campanas: () => import('./features/campanas/campanas.routes').then((m) => m.routes),
  empresa: () => import('./features/empresa/empresa.routes').then((m) => m.routes),
  sunat: () => import('./features/sunat/sunat.routes').then((m) => m.routes),
  guias: () => import('./features/guias/guias.routes').then((m) => m.routes),
  proformas: () => import('./features/proformas/proformas.routes').then((m) => m.routes),
  compras: () => import('./features/compras/compras.routes').then((m) => m.routes),
  insumos: () => import('./features/insumos/insumos.routes').then((m) => m.routes),
  produccion: () => import('./features/produccion/produccion.routes').then((m) => m.routes),
  contabilidad: () => import('./features/contabilidad/contabilidad.routes').then((m) => m.routes),
  bitacora: () => import('./features/bitacora/bitacora.routes').then((m) => m.routes),
  monitoreo: () => import('./features/monitoreo/monitoreo.routes').then((m) => m.routes),
};

function rutaDeModulo(op: OpcionMenu): Route {
  const cargar = MODULOS[op.ruta];
  const base: Route = { path: op.ruta, canActivate: [rolGuard], data: { roles: op.roles }, title: op.etiqueta };
  return cargar
    ? { ...base, loadChildren: cargar }
    : {
      ...base,
      loadComponent: aviso,
      data: {
        ...base.data,
        titulo: op.etiqueta,
        texto: 'Este módulo está en construcción. Pronto podrás usarlo desde aquí.',
        icono: op.icono,
      },
    };
}

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [invitadoGuard],
    title: 'Ingresar',
    loadComponent: () => import('./paginas/login/login').then((m) => m.Login),
  },
  {
    path: '',
    component: Shell,
    canActivate: [autenticadoGuard],
    canActivateChild: [autenticadoGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Inicio',
        loadComponent: () => import('./paginas/inicio/inicio').then((m) => m.Inicio),
      },
      {
        path: 'cambiar-password',
        title: 'Cambiar contraseña',
        loadComponent: () => import('./paginas/cambiar-password/cambiar-password').then((m) => m.CambiarPassword),
      },
      ...MENU.flatMap((g) => g.opciones).map(rutaDeModulo),
      {
        path: 'sin-permiso',
        title: 'Sin permiso',
        loadComponent: aviso,
        data: { titulo: 'Sin permiso', texto: 'Tu usuario no tiene acceso a esta sección.', icono: 'pi pi-lock' },
      },
      { path: '**', title: 'No encontrado', loadComponent: aviso },
    ],
  },
];
