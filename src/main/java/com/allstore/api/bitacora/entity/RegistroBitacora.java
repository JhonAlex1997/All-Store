package com.allstore.api.bitacora.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/**
 * Una acción sensible: quién, cuándo, desde dónde, qué y sobre qué. Inmutable (la base rechaza
 * modificarla o borrarla); no extiende BaseEntity porque no tiene "modificado por".
 */
@Entity
@Table(name = "bitacora")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroBitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccionBitacora accion;

    @Column(nullable = false, length = 40)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(length = 2000)
    private String detalle;

    @Column(length = 45)
    private String ip;

    @Column(length = 64)
    private String solicitud;

    public RegistroBitacora(OffsetDateTime fecha, String usuario, AccionBitacora accion, String entidad, Long entidadId,
                            String descripcion, String detalle, String ip, String solicitud) {
        this.fecha = fecha;
        this.usuario = usuario;
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.descripcion = descripcion;
        this.detalle = detalle;
        this.ip = ip;
        this.solicitud = solicitud;
    }
}
