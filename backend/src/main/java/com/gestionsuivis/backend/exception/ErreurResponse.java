package com.gestionsuivis.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.List;

/**
 * Corps JSON standard de toutes les réponses d'erreur de l'API (contrat commun avec React).
 * <p>
 * Exemple :
 * <pre>
 * {
 *   "timestamp": "2026-11-01T10:15:30Z",
 *   "status": 422,
 *   "code": "VALIDATION_ERROR",
 *   "message": "Les données envoyées sont invalides.",
 *   "path": "/api/projects",
 *   "errors": [ { "field": "name", "message": "Le nom du projet est obligatoire." } ]
 * }
 * </pre>
 * {@code code} reprend les valeurs de {@code ApiErrorCode} du frontend
 * ({@code src/types/api.ts}), plus {@code BAD_REQUEST}. Le champ {@code errors}
 * n'apparaît que s'il contient au moins un élément.
 * <p>
 * Ce DTO est aussi utilisé par les composants Spring Security (401 / 403 des filtres).
 */
public record ErreurResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ErreurChamp> errors) {

    public ErreurResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ErreurResponse de(HttpStatusCode statut, String message, String chemin) {
        return de(statut, message, chemin, List.of());
    }

    public static ErreurResponse de(HttpStatusCode statut, String message, String chemin,
                                    List<ErreurChamp> erreurs) {
        return new ErreurResponse(Instant.now(), statut.value(), MessagesErreur.code(statut),
                message, chemin, erreurs);
    }
}
