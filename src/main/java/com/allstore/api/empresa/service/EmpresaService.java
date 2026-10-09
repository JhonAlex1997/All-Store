package com.allstore.api.empresa.service;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.dto.EmpresaResponse;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    @Transactional(readOnly = true)
    public EmpresaResponse obtener() {
        return EmpresaResponse.from(obtenerEntidad());
    }

    /** Registra la empresa la primera vez y la actualiza después (siempre es la misma fila). */
    @Transactional
    public EmpresaResponse guardar(EmpresaRequest request) {
        String ruc = request.ruc().trim();
        TipoDocumentoIdentidad.RUC.validar(ruc).ifPresent(motivo -> {
            throw new BusinessException(motivo);
        });
        Empresa empresa = empresaRepository.findById(Empresa.ID_UNICO).orElseGet(Empresa::new);
        empresa.setRuc(ruc);
        empresa.setRazonSocial(request.razonSocial().trim());
        empresa.setNombreComercial(limpiar(request.nombreComercial()));
        empresa.setDireccionFiscal(request.direccionFiscal().trim());
        empresa.setUbigeo(request.ubigeo());
        empresa.setTelefono(limpiar(request.telefono()));
        empresa.setEmail(limpiar(request.email()) == null ? null : request.email().trim().toLowerCase());
        return EmpresaResponse.from(empresaRepository.save(empresa));
    }

    /** Para ventas y facturación: los datos del emisor que van en cada comprobante. */
    @Transactional(readOnly = true)
    public Empresa obtenerEntidad() {
        return empresaRepository.findById(Empresa.ID_UNICO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aún no se registraron los datos de la empresa (RUC, razón social, dirección fiscal)"));
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
