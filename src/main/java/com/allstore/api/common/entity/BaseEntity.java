package com.allstore.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.OptimisticLock;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Base de todas las entidades: id autoincremental, y fechas y usuario de creación/modificación
 * llenados automáticamente. Cada tabla debe declarar las columnas {@code id},
 * {@code created_at}, {@code updated_at}, {@code created_by} y {@code updated_by} en su
 * migración de Flyway.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Cambian junto con cualquier otro campo, así que no necesitan subir la versión por sí solos
    // (EntidadVersionada). Excluirlos permite que un cambio que no cuenta como edición (el último
    // acceso del usuario) no la suba por arrastre.
    @OptimisticLock(excluded = true)
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Usuario que creó el registro ("sistema" si fue un proceso automático). */
    @CreatedBy
    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    /** Último usuario que lo modificó. */
    @OptimisticLock(excluded = true)
    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
