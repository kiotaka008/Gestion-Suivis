package com.gestionsuivis.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Page de résultats renvoyée par tous les endpoints de liste, au format attendu
 * par le frontend ({@code PaginatedResponse<T>} dans {@code src/types/api.ts}) :
 * <pre>
 * {
 *   "data": [ ... ],
 *   "meta": { "page": 1, "perPage": 20, "total": 57, "totalPages": 3 }
 * }
 * </pre>
 * La numérotation des pages commence à 1.
 *
 * @param <T> type des DTO contenus dans la page
 */
public record PageResponse<T>(List<T> data, Meta meta) {

    public PageResponse {
        data = data == null ? List.of() : List.copyOf(data);
    }

    /** Métadonnées de pagination ({@code page} commence à 1). */
    public record Meta(int page, int perPage, long total, int totalPages) {
    }

    /** Construit la réponse à partir d'une page Spring Data déjà composée de DTO. */
    public static <T> PageResponse<T> de(Page<T> page) {
        return new PageResponse<>(page.getContent(),
                new Meta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    /**
     * Construit la réponse à partir d'une page d'entités, en convertissant chaque
     * élément avec un mapper (ex. {@code PageResponse.de(page, utilisateurMapper::versDto)}).
     */
    public static <E, T> PageResponse<T> de(Page<E> page, Function<? super E, ? extends T> conversion) {
        return de(page.map(conversion));
    }
}
