package com.allstore.api.seguridad.entity;

/**
 * Roles del sistema. Qué puede hacer cada uno está definido en un solo lugar:
 * {@link com.allstore.api.seguridad.config.SecurityConfig}.
 */
public enum Rol {
    /** Todo, incluida la configuración (empresa, series) y la gestión de usuarios. */
    ADMIN,
    /** Ventas y clientes. */
    CAJERO,
    /** Catálogo de productos (y luego inventario). */
    ALMACENERO
}
