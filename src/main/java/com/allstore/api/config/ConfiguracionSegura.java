package com.allstore.api.config;

import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * En producción (SUNAT en PRODUCCION) no se arranca con una configuración pensada para desarrollo:
 * datos demo (crean usuarios con una contraseña publicada en el repositorio), Swagger (publica el
 * mapa de la API), correos guardados como archivo, enlaces públicos sin https (el código secreto
 * del comprobante viajaría sin cifrar) o CORS abierto a cualquier sitio. Mejor no arrancar que
 * quedar expuesto sin darse cuenta.
 */
@Component
public class ConfiguracionSegura {

    public ConfiguracionSegura(Environment env, SunatProperties sunat) {
        if (sunat.entorno() != EntornoSunat.PRODUCCION) {
            return;
        }
        List<String> problemas = new ArrayList<>();
        if (env.getProperty("app.demo.cargar-datos", Boolean.class, false)) {
            problemas.add("app.demo.cargar-datos=true (crea usuarios demo con una contraseña conocida)");
        }
        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
                || env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)) {
            problemas.add("Swagger activo (springdoc.*.enabled=true)");
        }
        if ("ARCHIVO".equalsIgnoreCase(env.getProperty("app.entrega.correo.modo", ""))) {
            problemas.add("app.entrega.correo.modo=ARCHIVO (los correos no se envían, se guardan en disco)");
        }
        if (!env.getProperty("app.entrega.url-publica", "").startsWith("https://")) {
            problemas.add("URL_PUBLICA debe empezar con https:// (los enlaces de los clientes llevan un código secreto)");
        }
        if (env.getProperty("app.seguridad.cors-origenes", "").contains("*")) {
            problemas.add("CORS_ORIGENES no puede ser * (indica la dirección exacta del frontend)");
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Configuración insegura para producción:\n - " + String.join("\n - ", problemas));
        }
    }
}
