package com.allstore.api.seguridad.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * La ruta de la solicitud tal como la evalúan las reglas de acceso: decodificada y sin el contexto.
 * Los filtros propios deben comparar con esta y no con getRequestURI(): "/actuator/%70rometheus" o
 * "/api/auth/%6Cogin" llegan crudos en getRequestURI() pero Spring los atiende como "/actuator/prometheus"
 * y "/api/auth/login"; comparar la cruda dejaba saltarse el filtro.
 */
public final class RutaSolicitud {

    private static final UrlPathHelper RUTAS = new UrlPathHelper();

    static {
        RUTAS.setUrlDecode(true);
        RUTAS.setRemoveSemicolonContent(true);
    }

    private RutaSolicitud() {
    }

    public static String de(HttpServletRequest request) {
        return RUTAS.getPathWithinApplication(request);
    }
}
