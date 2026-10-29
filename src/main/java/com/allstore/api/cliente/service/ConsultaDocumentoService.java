package com.allstore.api.cliente.service;

import com.allstore.api.cliente.config.ConsultaDocumentosProperties;
import com.allstore.api.cliente.dto.ConsultaDocumentoResponse;
import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.repository.ClienteRepository;
import com.allstore.api.cliente.service.ProveedorDocumentos.DatosDocumento;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Datos de un RUC o DNI para no escribirlos a mano (y no equivocarse en el RUC de una factura, la
 * causa más común de rechazo). Primero valida el número (formato y dígito de control del RUC) sin
 * consultar a nadie; luego busca entre los clientes registrados; recién al final consulta al
 * proveedor, y recuerda la respuesta unas horas para no pagar dos veces el mismo número.
 */
@Slf4j
@Service
public class ConsultaDocumentoService {

    private static final int MAXIMO_EN_CACHE = 2000;

    private final ClienteRepository clienteRepository;
    private final ProveedorDocumentos proveedor;
    private final ConsultaDocumentosProperties properties;
    private final Clock reloj;
    private final Map<String, Recordado> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Recordado> mayor) {
            return size() > MAXIMO_EN_CACHE;
        }
    };

    private record Recordado(Optional<DatosDocumento> datos, Instant hasta) {
    }

    @Autowired
    public ConsultaDocumentoService(ClienteRepository clienteRepository, ProveedorDocumentos proveedor,
                                    ConsultaDocumentosProperties properties) {
        this(clienteRepository, proveedor, properties, Clock.systemUTC());
    }

    ConsultaDocumentoService(ClienteRepository clienteRepository, ProveedorDocumentos proveedor,
                             ConsultaDocumentosProperties properties, Clock reloj) {
        this.clienteRepository = clienteRepository;
        this.proveedor = proveedor;
        this.properties = properties;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ConsultaDocumentoResponse consultar(TipoDocumentoIdentidad tipo, String numero) {
        if (tipo != TipoDocumentoIdentidad.RUC && tipo != TipoDocumentoIdentidad.DNI) {
            throw new BusinessException("Solo se consultan RUC y DNI; los demás documentos se registran a mano");
        }
        String limpio = numero == null ? "" : numero.trim();
        tipo.validar(limpio).ifPresent(error -> {
            throw new BusinessException(error);
        });

        Optional<Cliente> registrado = clienteRepository.findByTipoDocumentoAndNumeroDocumento(tipo, limpio);
        if (registrado.isPresent()) {
            Cliente c = registrado.get();
            return new ConsultaDocumentoResponse(tipo, limpio, c.getNombre(), c.getDireccion(), c.getUbigeo(), null, null,
                    "REGISTRADO", c.getId(), null);
        }
        if (!properties.habilitada()) {
            throw new BusinessException("La consulta de RUC y DNI no está configurada (app.consulta-documentos): "
                    + "registra los datos del cliente a mano");
        }
        DatosDocumento datos = consultarProveedor(tipo, limpio)
                .orElseThrow(() -> new ResourceNotFoundException(tipo.getDescripcion() + " " + limpio + " no existe en "
                        + (tipo == TipoDocumentoIdentidad.RUC ? "el padrón de SUNAT" : "RENIEC")));
        return new ConsultaDocumentoResponse(tipo, limpio, datos.nombre(), datos.direccion(), datos.ubigeo(), datos.estado(),
                datos.condicion(), tipo == TipoDocumentoIdentidad.RUC ? "SUNAT" : "RENIEC", null, advertencia(tipo, datos));
    }

    private Optional<DatosDocumento> consultarProveedor(TipoDocumentoIdentidad tipo, String numero) {
        String clave = tipo + ":" + numero;
        Instant ahora = reloj.instant();
        synchronized (cache) {
            Recordado recordado = cache.get(clave);
            if (recordado != null && recordado.hasta().isAfter(ahora)) {
                return recordado.datos();
            }
        }
        try {
            Optional<DatosDocumento> datos = proveedor.consultar(tipo, numero);
            synchronized (cache) {
                cache.put(clave, new Recordado(datos, ahora.plus(Duration.ofHours(properties.horasCache()))));
            }
            return datos;
        } catch (IOException e) {
            log.warn("No se pudo consultar {} {}: {}", tipo, numero, e.getMessage());
            throw new BusinessException("No se pudo consultar el " + tipo.getDescripcion() + " ahora (" + e.getMessage()
                    + "); registra los datos a mano");
        }
    }

    /** Factura a un RUC que no está activo y habido: SUNAT la puede observar o el crédito fiscal no vale. */
    private static String advertencia(TipoDocumentoIdentidad tipo, DatosDocumento datos) {
        if (tipo != TipoDocumentoIdentidad.RUC) {
            return null;
        }
        boolean activo = datos.estado() == null || "ACTIVO".equalsIgnoreCase(datos.estado());
        boolean habido = datos.condicion() == null || "HABIDO".equalsIgnoreCase(datos.condicion());
        if (activo && habido) {
            return null;
        }
        return "El RUC está " + datos.estado() + " y " + datos.condicion() + ": no conviene emitirle factura";
    }
}
