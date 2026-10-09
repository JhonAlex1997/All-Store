package com.allstore.api.cliente.entity;

import com.allstore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cliente")
public class Cliente extends BaseEntity {

    @Convert(converter = TipoDocumentoIdentidadConverter.class)
    @Column(name = "tipo_documento", nullable = false, length = 1)
    private TipoDocumentoIdentidad tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 15)
    private String numeroDocumento;

    /** Nombres y apellidos, o razón social si es una empresa. */
    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 250)
    private String direccion;

    /** Ubigeo INEI de 6 dígitos. */
    @Column(length = 6)
    private String ubigeo;

    @Column(length = 20)
    private String telefono;

    @Column(length = 120)
    private String email;

    /** "Clientes varios": ventas menores sin identificar al comprador. No se edita. */
    @Column(nullable = false, updatable = false)
    private boolean generico;

    @Column(nullable = false)
    private boolean activo = true;
}
