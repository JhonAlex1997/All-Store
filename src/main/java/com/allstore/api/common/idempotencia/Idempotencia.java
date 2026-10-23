package com.allstore.api.common.idempotencia;

import com.allstore.api.common.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Reintentos sin duplicar. Si la respuesta no le llega al cliente (se cayó el wifi) y reintenta, o
 * el usuario hace doble clic, la operación no debe hacerse dos veces. El cliente envía la cabecera
 * Idempotency-Key; cada operación guarda la clave y la huella en lo que crea, en su misma
 * transacción, y un reintento con esa clave lo encuentra y lo devuelve en vez de crear otro.
 *
 * <pre>
 * SolicitudIdempotente solicitud = idempotencia.solicitud(clave, "operacion", request);
 * Optional&lt;X&gt; previo = idempotencia.previo(solicitud, repo::findByClaveIdempotencia, X::getHuellaSolicitud, ...);
 * if (previo.isPresent()) return respuesta(previo.get());
 * ... crear, guardando solicitud.clave() y solicitud.huella() ...
 * </pre>
 */
@Component
public class Idempotencia {

    private static final Pattern CLAVE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final JsonMapper json;

    @PersistenceContext
    private EntityManager entityManager;

    public Idempotencia(JsonMapper json) {
        this.json = json;
    }

    /** La solicitud con su clave (o NINGUNA si no vino la cabecera) y la huella de la operación y sus datos. */
    public SolicitudIdempotente solicitud(String clave, String operacion, Object... datos) {
        if (!StringUtils.hasText(clave)) {
            return SolicitudIdempotente.NINGUNA;
        }
        String limpia = clave.trim();
        if (!CLAVE.matcher(limpia).matches()) {
            throw new BusinessException("La cabecera " + SolicitudIdempotente.CABECERA + " debe tener de 8 a 64 caracteres "
                    + "(letras, números, - o _); lo usual es un UUID nuevo por cada operación");
        }
        List<Object> contenido = new ArrayList<>();
        contenido.add(operacion);
        contenido.addAll(Arrays.asList(datos));
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(contenido));
            return new SolicitudIdempotente(limpia, HexFormat.of().formatHex(sha));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * Lo que creó esta misma solicitud antes (es un reintento), o vacío si es nueva. Va al inicio
     * de la transacción, antes de validar: el reintento de algo ya hecho podría no pasar las
     * validaciones (ej. una nota de venta ya convertida).
     *
     * <p>Bloquea la clave hasta el final de la transacción: si llegan dos solicitudes con la misma
     * clave a la vez (doble clic), la segunda espera a que la primera termine y encuentra lo que creó.
     * La misma clave con otro contenido es un error del cliente.
     *
     * @param buscar      lo creado con esa clave
     * @param huella      la huella guardada en lo creado
     * @param descripcion cómo nombrar lo creado en el mensaje de error
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Optional<T> previo(SolicitudIdempotente solicitud, Function<String, Optional<T>> buscar,
                                  Function<T, String> huella, Function<T, String> descripcion) {
        if (!solicitud.presente()) {
            return Optional.empty();
        }
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtext(:clave))")
                .setParameter("clave", "idempotencia:" + solicitud.clave())
                .getSingleResult();
        Optional<T> previo = buscar.apply(solicitud.clave());
        previo.ifPresent(p -> {
            if (!solicitud.huella().equals(huella.apply(p))) {
                throw new BusinessException("La clave " + SolicitudIdempotente.CABECERA + " " + solicitud.clave()
                        + " ya se usó para otra solicitud (" + descripcion.apply(p) + "); usa una clave nueva por cada operación");
            }
        });
        return previo;
    }
}
