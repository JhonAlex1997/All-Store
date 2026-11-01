package com.allstore.api.compra.entity;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidadConverter;
import com.allstore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A quién se le compra mercadería, insumos o servicios (talleres de confección, lavanderías...). */
@Getter
@Setter
@Entity
@Table(name = "proveedor")
public class Proveedor extends BaseEntity {

    @Convert(converter = TipoDocumentoIdentidadConverter.class)
    @Column(name = "tipo_documento", nullable = false, length = 1)
    private TipoDocumentoIdentidad tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 15)
    private String numeroDocumento;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(length = 250)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Column(length = 120)
    private String email;

    @Column(nullable = false)
    private boolean activo = true;
}
