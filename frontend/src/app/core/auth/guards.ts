import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Rol, SesionService } from './sesion.service';

/** Exige sesión. Si el usuario debe cambiar su contraseña, solo lo deja entrar a esa pantalla. */
export const autenticadoGuard: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  if (!sesion.autenticado()) {
    return router.createUrlTree(['/login'], { queryParams: { volver: estado.url } });
  }
  if (sesion.debeCambiarPassword() && !estado.url.startsWith('/cambiar-password')) {
    return router.createUrlTree(['/cambiar-password']);
  }
  return true;
};

/** Solo para quien no ha iniciado sesión (pantalla de login). */
export const invitadoGuard: CanActivateFn = () => {
  const sesion = inject(SesionService);
  return sesion.autenticado() ? inject(Router).createUrlTree(['/']) : true;
};

/**
 * Restringe la ruta a los roles de `data.roles`. Es solo para la navegación: quien decide de verdad
 * es el backend, que responde 403 si el rol no corresponde.
 */
export const rolGuard: CanActivateFn = (ruta) => {
  const roles = (ruta.data['roles'] as Rol[] | undefined) ?? [];
  const sesion = inject(SesionService);
  return roles.length === 0 || sesion.tieneRol(...roles) ? true : inject(Router).createUrlTree(['/sin-permiso']);
};
