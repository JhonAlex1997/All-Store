import { menuPara } from './menu';

function rutas(rol: Parameters<typeof menuPara>[0]): string[] {
  return menuPara(rol).flatMap((g) => g.opciones.map((o) => o.ruta));
}

describe('menuPara', () => {
  it('el cajero ve la caja pero no compras ni gestión', () => {
    const r = rutas('CAJERO');
    expect(r).toContain('caja');
    expect(r).toContain('clientes');
    expect(r).not.toContain('compras');
    expect(r).not.toContain('reportes');
    expect(menuPara('CAJERO').map((g) => g.titulo)).not.toContain('Sistema');
  });

  it('el almacenero ve almacén pero no la caja', () => {
    const r = rutas('ALMACENERO');
    expect(r).toContain('compras');
    expect(r).toContain('produccion');
    expect(r).not.toContain('caja');
  });

  it('el administrador ve todo y sin sesión no hay menú', () => {
    expect(rutas('ADMIN')).toContain('usuarios');
    expect(menuPara(null)).toEqual([]);
  });
});
