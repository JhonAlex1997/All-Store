package com.allstore.api.bitacora.service;

import com.allstore.api.bitacora.dto.RegistroBitacoraResponse;
import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.entity.RegistroBitacora;
import com.allstore.api.bitacora.repository.BitacoraRepository;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.monitoreo.RegistroSolicitudes;
import com.allstore.api.seguridad.service.UsuarioEnSesion;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bitácora de acciones sensibles. Se registra dentro de la misma transacción de la acción: si la
 * acción se revierte, su registro también (no queda constancia de algo que no pasó), y si se
 * confirma, su registro no puede faltar.
 */
@Service
@RequiredArgsConstructor
public class Bitacora {

    /** Quien intentó ingresar sin lograrlo no tiene sesión. */
    public static final String ANONIMO = "anónimo";
    private static final int LARGO_DESCRIPCION = 500;
    private static final int LARGO_DETALLE = 2000;

    private final BitacoraRepository repository;
    private final UsuarioEnSesion usuarioEnSesion;

    /** Registra la acción a nombre del usuario de la sesión. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AccionBitacora accion, String entidad, Long entidadId, String descripcion, String detalle) {
        guardar(usuarioEnSesion.nombre(), accion, entidad, entidadId, descripcion, detalle);
    }

    /** Igual, sin detalle. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AccionBitacora accion, String entidad, Long entidadId, String descripcion) {
        registrar(accion, entidad, entidadId, descripcion, null);
    }

    /** A nombre de quien no tiene sesión (ej. el bloqueo por intentos fallidos al ingresar). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarAnonimo(AccionBitacora accion, String entidad, Long entidadId, String descripcion) {
        guardar(ANONIMO, accion, entidad, entidadId, descripcion, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistroBitacoraResponse> consultar(LocalDate desde, LocalDate hasta, String usuario,
                                                            List<AccionBitacora> acciones, String entidad, Long entidadId,
                                                            Pageable pageable) {
        Specification<RegistroBitacora> filtro = (raiz, consulta, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (desde != null) {
                p.add(cb.greaterThanOrEqualTo(raiz.get("fecha"), desde.atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime()));
            }
            if (hasta != null) {
                p.add(cb.lessThan(raiz.get("fecha"), hasta.plusDays(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime()));
            }
            if (StringUtils.hasText(usuario)) {
                p.add(cb.equal(cb.lower(raiz.get("usuario")), usuario.trim().toLowerCase()));
            }
            if (acciones != null && !acciones.isEmpty()) {
                p.add(raiz.get("accion").in(acciones));
            }
            if (StringUtils.hasText(entidad)) {
                p.add(cb.equal(raiz.get("entidad"), entidad.trim().toUpperCase()));
            }
            if (entidadId != null) {
                p.add(cb.equal(raiz.get("entidadId"), entidadId));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(filtro, pageable).map(RegistroBitacoraResponse::from));
    }

    private void guardar(String usuario, AccionBitacora accion, String entidad, Long entidadId, String descripcion,
                         String detalle) {
        repository.save(new RegistroBitacora(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA), usuario, accion, entidad,
                entidadId, recortar(descripcion, LARGO_DESCRIPCION), recortar(detalle, LARGO_DETALLE), ip(),
                RegistroSolicitudes.codigoActual()));
    }

    /** La IP de la solicitud en curso (detrás de Nginx, la real si FORWARD_HEADERS_STRATEGY=native). */
    private static String ip() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos
                ? atributos.getRequest().getRemoteAddr() : null;
    }

    private static String recortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }
}
