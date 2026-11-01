import { Rol } from '../auth/sesion.service';

export interface OpcionMenu {
  etiqueta: string;
  icono: string;
  /** Ruta del módulo (sin la barra inicial). */
  ruta: string;
  /** Roles que lo ven; replica los permisos de SecurityConfig en el backend. */
  roles: Rol[];
}

export interface GrupoMenu {
  titulo: string;
  opciones: OpcionMenu[];
}

const TODOS: Rol[] = ['ADMIN', 'CAJERO', 'ALMACENERO'];

export const MENU: GrupoMenu[] = [
  {
    titulo: 'Tienda',
    opciones: [
      { etiqueta: 'Caja', icono: 'pi pi-shopping-cart', ruta: 'caja', roles: ['ADMIN', 'CAJERO'] },
      { etiqueta: 'Ventas', icono: 'pi pi-receipt', ruta: 'ventas', roles: ['ADMIN', 'CAJERO'] },
      { etiqueta: 'Clientes', icono: 'pi pi-users', ruta: 'clientes', roles: ['ADMIN', 'CAJERO'] },
      { etiqueta: 'Guías de remisión', icono: 'pi pi-truck', ruta: 'guias', roles: TODOS },
    ],
  },
  {
    titulo: 'Almacén',
    opciones: [
      { etiqueta: 'Productos', icono: 'pi pi-tags', ruta: 'productos', roles: TODOS },
      { etiqueta: 'Inventario', icono: 'pi pi-box', ruta: 'inventario', roles: TODOS },
      { etiqueta: 'Compras', icono: 'pi pi-cart-arrow-down', ruta: 'compras', roles: ['ADMIN', 'ALMACENERO'] },
      { etiqueta: 'Insumos', icono: 'pi pi-th-large', ruta: 'insumos', roles: ['ADMIN', 'ALMACENERO'] },
      { etiqueta: 'Producción', icono: 'pi pi-cog', ruta: 'produccion', roles: ['ADMIN', 'ALMACENERO'] },
    ],
  },
  {
    titulo: 'Gestión',
    opciones: [
      { etiqueta: 'Campañas', icono: 'pi pi-percentage', ruta: 'campanas', roles: ['ADMIN'] },
      { etiqueta: 'Reportes', icono: 'pi pi-chart-bar', ruta: 'reportes', roles: ['ADMIN'] },
      { etiqueta: 'Contabilidad', icono: 'pi pi-book', ruta: 'contabilidad', roles: ['ADMIN'] },
      { etiqueta: 'SUNAT', icono: 'pi pi-send', ruta: 'sunat', roles: ['ADMIN'] },
    ],
  },
  {
    titulo: 'Sistema',
    opciones: [
      { etiqueta: 'Usuarios', icono: 'pi pi-user-edit', ruta: 'usuarios', roles: ['ADMIN'] },
      { etiqueta: 'Empresa y series', icono: 'pi pi-building', ruta: 'empresa', roles: ['ADMIN'] },
      { etiqueta: 'Bitácora', icono: 'pi pi-history', ruta: 'bitacora', roles: ['ADMIN'] },
      { etiqueta: 'Monitoreo', icono: 'pi pi-heart', ruta: 'monitoreo', roles: ['ADMIN'] },
    ],
  },
];

/** Menú visible para un rol: sin opciones ajenas ni grupos vacíos. */
export function menuPara(rol: Rol | null): GrupoMenu[] {
  if (!rol) {
    return [];
  }
  return MENU
    .map((g) => ({ ...g, opciones: g.opciones.filter((o) => o.roles.includes(rol)) }))
    .filter((g) => g.opciones.length > 0);
}
