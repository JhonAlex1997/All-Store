package com.allstore.api.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Página de resultados con formato estable para el frontend (page empieza en 0). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
