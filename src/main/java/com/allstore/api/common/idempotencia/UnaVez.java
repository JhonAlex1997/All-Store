package com.allstore.api.common.idempotencia;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Protección general contra doble clic y reintentos para lo que no guarda la clave en lo que crea
 * (compras, producción, conteos, insumos, campañas, entregas, altas del catálogo...).
 *
 * <pre>
 * return unaVez.ejecutar(clave, "compra", CompraResponse.class, () -&gt; compraService.registrar(request), request);
 * </pre>
 *
 * Sin la cabecera Idempotency-Key, la operación se ejecuta siempre. Con ella, la operación y el registro
 * de la solicitud van en la misma transacción: si la operación falla no queda registro (el reintento la
 * vuelve a intentar) y si se confirma, un reintento con la misma clave recibe la misma respuesta sin
 * repetirla. Dos solicitudes simultáneas con la misma clave se ordenan (la segunda espera y recibe la
 * respuesta de la primera). La misma clave con otro contenido, otra operación u otro usuario es un error.
 */
@Slf4j
@Component
public class UnaVez {

    /** Lo que dura un reintento razonable (la caja que perdió la conexión y vuelve). */
    static final int DIAS_CONSERVADA = 7;

    private final Idempotencia idempotencia;
    private final SolicitudProcesadaRepository repository;
    private final AuditorAware<String> usuarioActual;
    private final JsonMapper json;

    @PersistenceContext
    private EntityManager entityManager;

    public UnaVez(Idempotencia idempotencia, SolicitudProcesadaRepository repository, AuditorAware<String> usuarioActual,
                  JsonMapper json) {
        this.idempotencia = idempotencia;
        this.repository = repository;
        this.usuarioActual = usuarioActual;
        this.json = json;
    }

    @Transactional
    public <T> T ejecutar(String clave, String operacion, Class<T> tipo, Supplier<T> accion, Object... datos) {
        return ejecutar(clave, operacion, json.constructType(tipo), accion, datos);
    }

    @Transactional
    public <T> T ejecutar(String clave, String operacion, TypeReference<T> tipo, Supplier<T> accion, Object... datos) {
        return ejecutar(clave, operacion, json.constructType(tipo), accion, datos);
    }

    private <T> T ejecutar(String clave, String operacion, JavaType tipo, Supplier<T> accion, Object[] datos) {
        String usuario = usuarioActual.getCurrentAuditor().orElse(JpaAuditingConfig.USUARIO_SISTEMA);
        List<Object> contenido = new ArrayList<>();
        contenido.add(usuario);
        contenido.addAll(Arrays.asList(datos));
        SolicitudIdempotente solicitud = idempotencia.solicitud(clave, operacion, contenido.toArray());
        if (!solicitud.presente()) {
            return accion.get();
        }
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtext(:clave))")
                .setParameter("clave", "idempotencia:" + solicitud.clave())
                .getSingleResult();
        Optional<SolicitudProcesada> previa = repository.findById(solicitud.clave());
        if (previa.isPresent()) {
            SolicitudProcesada p = previa.get();
            if (!p.getHuella().equals(solicitud.huella())) {
                throw new BusinessException("La clave " + SolicitudIdempotente.CABECERA + " " + solicitud.clave()
                        + " ya se usó para otra solicitud (" + p.getOperacion() + "); usa una clave nueva por cada operación");
            }
            log.info("Reintento de {} con la clave {}: se devuelve la respuesta ya dada", operacion, solicitud.clave());
            return p.getRespuesta() == null ? null : json.readValue(p.getRespuesta(), tipo);
        }
        T resultado = accion.get();
        repository.save(new SolicitudProcesada(solicitud.clave(), usuario, operacion, solicitud.huella(),
                resultado == null ? null : json.writeValueAsString(resultado), OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA)));
        return resultado;
    }

    /** Cada madrugada borra las solicitudes de hace más de una semana. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Lima")
    @Transactional
    public void limpiar() {
        int borradas = repository.borrarAnteriores(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA).minusDays(DIAS_CONSERVADA));
        if (borradas > 0) {
            log.info("Se borraron {} solicitudes procesadas de hace más de {} días", borradas, DIAS_CONSERVADA);
        }
    }
}
