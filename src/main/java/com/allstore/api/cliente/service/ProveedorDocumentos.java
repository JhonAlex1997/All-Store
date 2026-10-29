package com.allstore.api.cliente.service;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;

import java.io.IOException;
import java.util.Optional;

/** Quien responde los datos de un RUC (SUNAT) o un DNI (RENIEC). */
public interface ProveedorDocumentos {

    /**
     * @return los datos, o vacío si el número no existe
     * @throws IOException sin comunicación o con el proveedor mal configurado
     */
    Optional<DatosDocumento> consultar(TipoDocumentoIdentidad tipo, String numero) throws IOException;

    /**
     * @param estado    solo RUC: ACTIVO, BAJA DE OFICIO, SUSPENSION TEMPORAL...
     * @param condicion solo RUC: HABIDO, NO HABIDO...
     */
    record DatosDocumento(String nombre, String direccion, String ubigeo, String estado, String condicion) {
    }
}
