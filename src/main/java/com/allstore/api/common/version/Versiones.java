package com.allstore.api.common.version;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Bloqueo optimista de los formularios de edición.
 * <p>
 * Al abrir el formulario la pantalla guarda la {@code version} que vino con el registro y al guardar
 * la manda en la cabecera {@code If-Match}. Si no coincide con la actual, alguien guardó en medio y se
 * responde 412 con quién y cuándo, para que recargue en lugar de pisar esos cambios. Sin cabecera no
 * se verifica (clientes de la API que no la usen siguen funcionando como antes).
 */
public final class Versiones {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private Versiones() {
    }

    /** Lee la cabecera If-Match: {@code 3}, {@code "3"} o {@code W/"3"}. Vacía o {@code *}: sin verificación. */
    public static Long deIfMatch(String ifMatch) {
        if (ifMatch == null) {
            return null;
        }
        String valor = ifMatch.trim();
        if (valor.startsWith("W/")) {
            valor = valor.substring(2);
        }
        valor = valor.replace("\"", "").trim();
        if (valor.isEmpty() || valor.equals("*")) {
            return null;
        }
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            throw new BusinessException("La cabecera If-Match debe traer la versión del registro (un número)");
        }
    }

    /**
     * @param esperada   versión que leyó la pantalla (null: no verificar)
     * @param actual     versión guardada ahora
     * @param registro   cómo nombrarlo en el mensaje ("este cliente", "la empresa")
     * @param porQuien   último usuario que lo modificó
     * @param cuando     cuándo lo modificó
     */
    public static void verificar(Long esperada, long actual, String registro, String porQuien, OffsetDateTime cuando) {
        if (esperada == null || esperada == actual) {
            return;
        }
        StringBuilder quien = new StringBuilder("Otra persona");
        if (porQuien != null && !porQuien.isBlank()) {
            quien.append(" (").append(porQuien).append(")");
        }
        if (cuando != null) {
            quien.append(" modificó ").append(registro).append(" el ")
                    .append(HORA.format(cuando.atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA)));
        } else {
            quien.append(" modificó ").append(registro);
        }
        throw new EdicionConcurrenteException(quien
                + " mientras lo editabas. Recarga para ver sus cambios y vuelve a aplicar los tuyos");
    }

    public static void verificar(Long esperada, com.allstore.api.common.entity.EntidadVersionada entidad, String registro) {
        verificar(esperada, entidad.getVersion(), registro, entidad.getUpdatedBy(), entidad.getUpdatedAt());
    }
}
