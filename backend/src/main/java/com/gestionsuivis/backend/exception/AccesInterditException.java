package com.gestionsuivis.backend.exception;

/**
 * Levée par un service lorsque l'utilisateur connecté n'a pas le droit d'accéder
 * à une ressource précise (HTTP 403).
 * <p>
 * Exemple : un EMPLOYE qui demande l'activité d'un autre employé.
 */
public class AccesInterditException extends RuntimeException {

    public AccesInterditException() {
        super(MessagesErreur.ACCES_INTERDIT);
    }

    public AccesInterditException(String message) {
        super(message);
    }
}
