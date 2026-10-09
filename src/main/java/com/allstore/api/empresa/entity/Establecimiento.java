package com.allstore.api.empresa.entity;

import com.allstore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Local de la empresa (tienda, almacén, taller) registrado en SUNAT como domicilio o anexo. */
@Getter
@Setter
@Entity
@Table(name = "establecimiento")
public class Establecimiento extends BaseEntity {

    public static final String CODIGO_DOMICILIO_FISCAL = "0000";

    /** Código de anexo SUNAT (0000 = domicilio fiscal). */
    @Column(nullable = false, length = 4)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEstablecimiento tipo;

    @Column(nullable = false, length = 250)
    private String direccion;

    @Column(nullable = false, length = 6)
    private String ubigeo;

    @Column(nullable = false)
    private boolean activo = true;
}
