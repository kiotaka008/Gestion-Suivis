package com.gestionsuivis.backend.exception;

/**
 * Levée par un service lorsqu'une règle métier empêche l'opération (HTTP 409).
 * <p>
 * Exemples : email déjà utilisé dans l'organisation, suppression d'un utilisateur
 * encore chef de projet, retrait du chef de projet des membres.
 */
public class ConflitMetierException extends RuntimeException {

    public ConflitMetierException(String message) {
        super(message);
    }
}
