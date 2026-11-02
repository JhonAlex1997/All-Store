package com.allstore.api.caja.service;

import com.allstore.api.caja.dto.ProductoCajaResponse;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.inventario.repository.StockRepository;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.repository.ProductoVarianteRepository;
import com.allstore.api.promocion.service.CampanaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Búsqueda de prendas para la caja: una sola consulta con tallas, colores, stock del local y campañas. */
@Service
@RequiredArgsConstructor
public class CatalogoCajaService {

    /** Suficiente para un par de modelos con todas sus tallas y colores sin cargar el catálogo entero. */
    static final int MAXIMO_PRENDAS = 150;
    static final int LARGO_MINIMO = 2;

    private final ProductoVarianteRepository varianteRepository;
    private final StockRepository stockRepository;
    private final CampanaService campanaService;

    @Transactional(readOnly = true)
    public List<ProductoCajaResponse> buscar(String busqueda, Long establecimientoId) {
        String texto = busqueda == null ? "" : busqueda.trim();
        if (texto.length() < LARGO_MINIMO) {
            return List.of();
        }
        String patron = "%" + texto.toUpperCase(Locale.ROOT) + "%";
        List<ProductoVariante> variantes = varianteRepository.buscarParaVenta(patron, texto, PageRequest.of(0, MAXIMO_PRENDAS));
        if (variantes.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> stock = new HashMap<>();
        stockRepository.cantidades(variantes.stream().map(ProductoVariante::getId).toList(), establecimientoId)
                .forEach(fila -> stock.put((Long) fila[0], ((Number) fila[1]).intValue()));
        Map<Long, CampanaService.Oferta> ofertas = campanaService.mejoresOfertas(variantes, establecimientoId,
                LocalDate.now(JpaAuditingConfig.ZONA_LIMA));

        Map<Long, List<ProductoCajaResponse.Variante>> porModelo = new LinkedHashMap<>();
        Map<Long, ProductoVariante> primera = new HashMap<>();
        for (ProductoVariante v : variantes) {
            Long productoId = v.getProducto().getId();
            primera.putIfAbsent(productoId, v);
            CampanaService.Oferta oferta = ofertas.get(v.getId());
            porModelo.computeIfAbsent(productoId, id -> new ArrayList<>()).add(new ProductoCajaResponse.Variante(
                    v.getId(), v.getSku(), v.getCodigoBarras(), v.getTalla().getCodigo(), v.getColor().getNombre(),
                    v.getPrecioEfectivo(), v.getPrecioPorMayorEfectivo(),
                    oferta != null ? oferta.precio() : null, oferta != null ? oferta.campana().getNombre() : null,
                    stock.getOrDefault(v.getId(), 0)));
        }
        return porModelo.entrySet().stream().map(e -> {
            var producto = primera.get(e.getKey()).getProducto();
            return new ProductoCajaResponse(producto.getId(), producto.getCodigo(), producto.getNombre(),
                    StringUtils.hasText(producto.getCategoria().getNombre()) ? producto.getCategoria().getNombre() : null,
                    e.getValue());
        }).toList();
    }
}
