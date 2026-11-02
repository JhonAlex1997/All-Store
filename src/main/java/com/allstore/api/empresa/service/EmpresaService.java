package com.allstore.api.empresa.service;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.dto.EmpresaResponse;
import com.allstore.api.empresa.dto.MarcaResponse;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final Bitacora bitacora;

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
        String antes = empresa.getRuc() == null ? null : resumen(empresa);
        empresa.setRuc(ruc);
        empresa.setRazonSocial(request.razonSocial().trim());
        empresa.setNombreComercial(limpiar(request.nombreComercial()));
        empresa.setDireccionFiscal(request.direccionFiscal().trim());
        empresa.setUbigeo(request.ubigeo());
        empresa.setTelefono(limpiar(request.telefono()));
        empresa.setEmail(limpiar(request.email()) == null ? null : request.email().trim().toLowerCase());
        if (request.minimoPrendasPorMayor() != null) {
            empresa.setMinimoPrendasPorMayor(request.minimoPrendasPorMayor());
        }
        Empresa guardada = empresaRepository.save(empresa);
        String despues = resumen(guardada);
        if (!despues.equals(antes)) {
            bitacora.registrar(AccionBitacora.EMPRESA_MODIFICADA, "EMPRESA", guardada.getId(),
                    antes == null ? "Registró la empresa " + guardada.getRazonSocial() : "Cambió los datos de la empresa",
                    antes == null ? despues : "Antes: " + antes + ". Después: " + despues);
        }
        return EmpresaResponse.from(guardada);
    }

    /** Lo que identifica al emisor ante SUNAT y la regla del por mayor: lo que importa auditar. */
    private static String resumen(Empresa e) {
        return "RUC " + e.getRuc() + ", " + e.getRazonSocial() + ", " + e.getDireccionFiscal() + " (" + e.getUbigeo()
                + "), por mayor desde " + e.getMinimoPrendasPorMayor() + " prendas";
    }

    /** Para ventas y facturación: los datos del emisor que van en cada comprobante. */
    @Transactional(readOnly = true)
    public Empresa obtenerEntidad() {
        return empresaRepository.findById(Empresa.ID_UNICO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aún no se registraron los datos de la empresa (RUC, razón social, dirección fiscal)"));
    }

    /**
     * Igual que obtenerEntidad pero sin lanzar excepción si no existe. La usa la facturación
     * dentro de la transacción de una venta: una excepción, aunque se capture, dejaría esa
     * transacción marcada para deshacerse.
     */
    @Transactional(readOnly = true)
    public Optional<Empresa> buscarEntidad() {
        return empresaRepository.findById(Empresa.ID_UNICO);
    }

    /** Nombre con que se presenta la tienda en el sistema, los correos y las alertas. */
    @Transactional(readOnly = true)
    public MarcaResponse marca() {
        return buscarEntidad()
                .map(e -> new MarcaResponse(nombreVisible(e), true))
                .orElseGet(() -> new MarcaResponse(MarcaResponse.SIN_CONFIGURAR, false));
    }

    /** Nombre comercial; si no se registró, la razón social. */
    public static String nombreVisible(Empresa e) {
        return StringUtils.hasText(e.getNombreComercial()) ? e.getNombreComercial() : e.getRazonSocial();
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
