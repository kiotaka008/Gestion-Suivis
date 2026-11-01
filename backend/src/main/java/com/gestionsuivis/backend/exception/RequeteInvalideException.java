package com.gestionsuivis.backend.exception;

/**
 * Levée par un service lorsqu'une demande est incohérente alors que chaque champ,
 * pris isolément, est valide (HTTP 422, code {@code VALIDATION_ERROR}).
 * <p>
 * Exemple : {@code new RequeteInvalideException("dueDate", "L'échéance doit être postérieure à la date de début.")}
 * produit {@code errors: [{ "field": "dueDate", "message": "..." }]}.
 */
public class RequeteInvalideException extends RuntimeException {

    private final String champ;

    public RequeteInvalideException(String message) {
        this(null, message);
    }

    public RequeteInvalideException(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    /** Champ concerné, ou {@code null} si l'erreur porte sur l'ensemble de la requête. */
    public String getChamp() {
        return champ;
    }
}
