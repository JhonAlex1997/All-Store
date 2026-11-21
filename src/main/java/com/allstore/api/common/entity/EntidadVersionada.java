package com.allstore.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * Entidad que se edita desde un formulario: lleva un número de versión que Hibernate sube en cada
 * cambio. La pantalla manda la versión que leyó (cabecera {@code If-Match}); si otra persona guardó
 * antes, la versión ya no coincide y se rechaza en lugar de pisar sus cambios sin aviso
 * ({@link com.allstore.api.common.version.Versiones}).
 */
@Getter
@MappedSuperclass
public abstract class EntidadVersionada extends BaseEntity {

    @Version
    @Column(nullable = false)
    private long version;
}
