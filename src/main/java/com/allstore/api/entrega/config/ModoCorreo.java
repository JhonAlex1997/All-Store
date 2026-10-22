package com.allstore.api.entrega.config;

public enum ModoCorreo {
    /** No se envían correos (el envío manual responde que falta configurarlo). */
    DESACTIVADO,
    /** Se envían por el servidor de correo configurado en spring.mail.*. */
    SMTP,
    /** Desarrollo y pruebas: cada correo se guarda como archivo .eml (se abre con Outlook o Thunderbird). */
    ARCHIVO
}
