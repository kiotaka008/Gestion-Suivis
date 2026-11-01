package com.gestionsuivis.backend.exception;

/**
 * Levée par un service lorsqu'une règle métier empêche l'opération (HTTP 409).
 * <p>
 * Exemples : email déjà utilisé, suppression d'un département qui contient encore
 * des employés.
 */
public class ConflitMetierException extends RuntimeException {

    public ConflitMetierException(String message) {
        super(message);
    }
}
