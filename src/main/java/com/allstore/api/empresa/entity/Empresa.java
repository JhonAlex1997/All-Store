package com.allstore.api.empresa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * El emisor de los comprobantes. Hay una sola fila con id fijo 1, por eso no extiende
 * BaseEntity (que genera el id): un id autogenerado podría saltar a 2 tras un fallo y la
 * tabla, que solo admite id = 1, ya no aceptaría la empresa.
 */
@Getter
@Setter
@Entity
@Table(name = "empresa")
@EntityListeners(AuditingEntityListener.class)
public class Empresa {

    public static final Long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    @Column(nullable = false, length = 11)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 200)
    private String nombreComercial;

    @Column(name = "direccion_fiscal", nullable = false, length = 250)
    private String direccionFiscal;

    @Column(nullable = false, length = 6)
    private String ubigeo;

    @Column(length = 20)
    private String telefono;

    @Column(length = 120)
    private String email;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
