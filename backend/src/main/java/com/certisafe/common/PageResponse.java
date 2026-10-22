package com.certisafe.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Formato UNICO de respuesta paginada de toda la API.
 *
 * <p>No se devuelve {@code Page} de Spring directamente: su serializacion no es estable
 * entre versiones. El frontend Angular depende de esta forma exacta.</p>
 *
 * <pre>
 * {
 *   "content": [ ... ],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 137,
 *   "totalPages": 7,
 *   "first": true,
 *   "last": false
 * }
 * </pre>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }
}
