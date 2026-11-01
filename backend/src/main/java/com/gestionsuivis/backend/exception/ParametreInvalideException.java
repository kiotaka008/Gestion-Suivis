package com.gestionsuivis.backend.exception;

/**
 * Paramètre d'URL ou de requête illisible ou non pris en charge (HTTP 400, code
 * {@code BAD_REQUEST}). Exemples : {@code ?status=archived}, {@code ?sortBy=motDePasse}.
 */
public class ParametreInvalideException extends RuntimeException {

    public ParametreInvalideException(String message) {
        super(message);
    }
}
