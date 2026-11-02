package com.allstore.api.empresa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/** Logo de la tienda en PNG. Una sola fila con id fijo 1, como la empresa. */
@Getter
@Setter
@Entity
@Table(name = "empresa_logo")
@EntityListeners(AuditingEntityListener.class)
public class EmpresaLogo {

    @Id
    private Long id = Empresa.ID_UNICO;

    @Column(nullable = false)
    private byte[] contenido;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
