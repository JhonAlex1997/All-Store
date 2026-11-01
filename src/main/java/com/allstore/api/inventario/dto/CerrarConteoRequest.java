package com.allstore.api.inventario.dto;

/**
 * @param noContadosEnCero true: lo que no se contó se toma como que no hay (se ajusta a 0). false (por
 *                         defecto): lo no contado no se toca
 */
public record CerrarConteoRequest(boolean noContadosEnCero) {
}
