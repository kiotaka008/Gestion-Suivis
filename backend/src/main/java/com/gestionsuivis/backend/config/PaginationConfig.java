package com.gestionsuivis.backend.config;

import com.gestionsuivis.backend.exception.ParametreInvalideException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

import java.util.Map;

/**
 * Règles communes de pagination, alignées sur le frontend
 * ({@code PaginationParams} dans {@code src/types/api.ts}) :
 * <ul>
 *   <li>{@code page} : numéro de page, à partir de 1 ;</li>
 *   <li>{@code perPage} : taille de page, {@value #TAILLE_PAR_DEFAUT} par défaut,
 *       ramenée à {@value #TAILLE_MAXIMALE} au-delà ;</li>
 *   <li>{@code sortBy} : champ de tri, choisi dans la liste autorisée de chaque endpoint ;</li>
 *   <li>{@code sortOrder} : {@code asc} (défaut) ou {@code desc}.</li>
 * </ul>
 * Les contrôleurs utilisent {@link #creer(Integer, Integer, String, String, Map, Sort)}.
 * Le résolveur {@code Pageable} de Spring Data est aussi aligné (page à partir de 1,
 * paramètre {@code perPage}) pour les éventuels usages directs.
 */
@Configuration
public class PaginationConfig {

    public static final int TAILLE_PAR_DEFAUT = 20;
    public static final int TAILLE_MAXIMALE = 100;

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolveur -> {
            resolveur.setOneIndexedParameters(true);
            resolveur.setSizeParameterName("perPage");
            resolveur.setMaxPageSize(TAILLE_MAXIMALE);
            resolveur.setFallbackPageable(PageRequest.of(0, TAILLE_PAR_DEFAUT));
        };
    }

    /**
     * Construit la requête de page à partir des paramètres du frontend.
     *
     * @param page          numéro de page (1 par défaut, valeurs &lt; 1 ramenées à 1)
     * @param perPage       taille (20 par défaut, ramenée entre 1 et 100)
     * @param sortBy        champ de tri de l'API (camelCase), {@code null} pour le tri par défaut
     * @param sortOrder     {@code asc} ou {@code desc}
     * @param triAutorises  correspondance champ API → propriété de l'entité
     * @param triParDefaut  tri appliqué si {@code sortBy} est absent
     * @throws ParametreInvalideException si {@code sortBy} ou {@code sortOrder} n'est pas reconnu
     */
    public static Pageable creer(Integer page, Integer perPage, String sortBy, String sortOrder,
                                 Map<String, String> triAutorises, Sort triParDefaut) {
        int numero = page == null || page < 1 ? 0 : page - 1;
        int taille = perPage == null || perPage < 1 ? TAILLE_PAR_DEFAUT : Math.min(perPage, TAILLE_MAXIMALE);
        Sort.Direction direction = lireDirection(sortOrder);
        Sort tri;
        if (sortBy == null || sortBy.isBlank()) {
            tri = triParDefaut;
        } else {
            String propriete = triAutorises.get(sortBy.trim());
            if (propriete == null) {
                throw new ParametreInvalideException("Le critère de tri '" + sortBy.trim()
                        + "' n'est pas reconnu. Valeurs acceptées : "
                        + String.join(", ", triAutorises.keySet().stream().sorted().toList()) + ".");
            }
            tri = Sort.by(direction, propriete);
        }
        return PageRequest.of(numero, taille, tri);
    }

    private static Sort.Direction lireDirection(String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank() || sortOrder.trim().equalsIgnoreCase("asc")) {
            return Sort.Direction.ASC;
        }
        if (sortOrder.trim().equalsIgnoreCase("desc")) {
            return Sort.Direction.DESC;
        }
        throw new ParametreInvalideException("Le paramètre 'sortOrder' doit valoir 'asc' ou 'desc'.");
    }
}
