package com.allstore.api.promocion.service;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.promocion.dto.CampanaRequest;
import com.allstore.api.promocion.dto.CampanaResponse;
import com.allstore.api.promocion.dto.PrecioCampanaResponse;
import com.allstore.api.promocion.entity.Campana;
import com.allstore.api.promocion.entity.CampanaAlcance;
import com.allstore.api.promocion.entity.TipoCampana;
import com.allstore.api.promocion.repository.CampanaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Campañas de promoción. La venta pide la mejor oferta vigente de cada prenda: si varias campañas
 * aplican, gana la que deja el precio más bajo (no se acumulan entre sí).
 */
@Service
@RequiredArgsConstructor
public class CampanaService {

    private final CampanaRepository campanaRepository;
    private final EstablecimientoService establecimientoService;
    private final ProductoService productoService;
    private final Bitacora bitacora;

    /** La campaña elegida para una prenda y el precio al que la deja. */
    public record Oferta(Campana campana, BigDecimal precio) {
    }

    @Transactional
    public CampanaResponse crear(CampanaRequest request) {
        Campana campana = new Campana();
        aplicar(campana, request);
        campanaRepository.save(campana);
        registrar(campana, "Creó");
        return CampanaResponse.from(campana, hoy());
    }

    @Transactional
    public CampanaResponse actualizar(Long id, CampanaRequest request) {
        Campana campana = obtenerEntidad(id);
        aplicar(campana, request);
        registrar(campana, "Cambió");
        return CampanaResponse.from(campana, hoy());
    }

    @Transactional(readOnly = true)
    public List<CampanaResponse> listar(boolean soloVigentes) {
        LocalDate hoy = hoy();
        return (soloVigentes ? campanaRepository.vigentes(hoy) : campanaRepository.todas()).stream()
                .map(c -> CampanaResponse.from(c, hoy)).toList();
    }

    @Transactional(readOnly = true)
    public CampanaResponse obtener(Long id) {
        return CampanaResponse.from(obtenerEntidad(id), hoy());
    }

    /** Precio de hoy de cada prenda en un local, con la campaña que lo da (para la caja). */
    @Transactional(readOnly = true)
    public List<PrecioCampanaResponse> precios(Collection<Long> varianteIds, Long establecimientoId) {
        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(varianteIds);
        Map<Long, Oferta> ofertas = mejoresOfertas(variantes.values(), establecimientoId, hoy());
        return variantes.values().stream().map(v -> {
            Oferta o = ofertas.get(v.getId());
            return new PrecioCampanaResponse(v.getId(), v.getSku(), v.getPrecioEfectivo(), o != null ? o.precio() : null,
                    o != null ? o.campana().getId() : null, o != null ? o.campana().getNombre() : null);
        }).toList();
    }

    /** La mejor oferta vigente de cada prenda ese día en ese local (solo las prendas con alguna). */
    @Transactional(readOnly = true)
    public Map<Long, Oferta> mejoresOfertas(Collection<ProductoVariante> variantes, Long establecimientoId, LocalDate dia) {
        List<Campana> vigentes = campanaRepository.vigentes(dia).stream().filter(c -> c.vigente(dia, establecimientoId)).toList();
        Map<Long, Oferta> ofertas = new HashMap<>();
        if (vigentes.isEmpty()) {
            return ofertas;
        }
        for (ProductoVariante v : variantes) {
            BigDecimal normal = v.getPrecioEfectivo();
            Long productoId = v.getProducto().getId();
            Long categoriaId = v.getProducto().getCategoria().getId();
            vigentes.stream()
                    .filter(c -> c.aplicaA(v.getId(), productoId, categoriaId))
                    .map(c -> new Oferta(c, c.getTipo().aplicar(normal, c.getValor())))
                    .filter(o -> o.precio().compareTo(normal) < 0)
                    .min((a, b) -> a.precio().compareTo(b.precio()))
                    .ifPresent(o -> ofertas.put(v.getId(), o));
        }
        return ofertas;
    }

    private void aplicar(Campana campana, CampanaRequest r) {
        if (r.fechaFin().isBefore(r.fechaInicio())) {
            throw new BusinessException("La campaña no puede terminar antes de empezar");
        }
        if (r.tipo() == TipoCampana.PORCENTAJE && r.valor().compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new BusinessException("El porcentaje debe ser menor a 100");
        }
        campana.setNombre(r.nombre().trim());
        campana.setDescripcion(StringUtils.hasText(r.descripcion()) ? r.descripcion().trim() : null);
        campana.setTipo(r.tipo());
        campana.setValor(r.valor());
        campana.setFechaInicio(r.fechaInicio());
        campana.setFechaFin(r.fechaFin());
        campana.setEstablecimiento(r.establecimientoId() != null ? establecimientoService.obtener(r.establecimientoId()) : null);
        campana.setActivo(r.activo() == null || r.activo());
        List<CampanaAlcance> alcance = new ArrayList<>();
        Optional.ofNullable(r.categoriaIds()).orElse(List.of()).stream().distinct().forEach(id -> alcance.add(new CampanaAlcance(id, null, null)));
        Optional.ofNullable(r.productoIds()).orElse(List.of()).stream().distinct().forEach(id -> alcance.add(new CampanaAlcance(null, id, null)));
        List<Long> varianteIds = Optional.ofNullable(r.varianteIds()).orElse(List.of()).stream().distinct().toList();
        if (!varianteIds.isEmpty()) {
            productoService.obtenerVariantes(varianteIds);
        }
        varianteIds.forEach(id -> alcance.add(new CampanaAlcance(null, null, id)));
        campana.reemplazarAlcance(alcance);
    }

    private void registrar(Campana c, String verbo) {
        bitacora.registrar(AccionBitacora.CAMPANA_GUARDADA, "CAMPANA", c.getId(), verbo + " la campaña " + c.getNombre()
                        + " (" + c.getTipo() + " " + c.getValor() + ", del " + c.getFechaInicio() + " al " + c.getFechaFin() + ")",
                (c.getAlcance().isEmpty() ? "Todo el catálogo" : c.getAlcance().size() + " categorías, modelos o prendas")
                        + (c.getEstablecimiento() != null ? " en " + c.getEstablecimiento().getNombre() : " en todos los locales")
                        + (c.isActivo() ? "" : ". Inactiva"));
    }

    private Campana obtenerEntidad(Long id) {
        return campanaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Campaña", id));
    }

    private static LocalDate hoy() {
        return LocalDate.now(JpaAuditingConfig.ZONA_LIMA);
    }
}
