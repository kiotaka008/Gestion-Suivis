package com.gestionsuivis.backend.exception;

/**
 * Levée par un service lorsqu'une ressource demandée n'existe pas (HTTP 404).
 * <p>
 * Exemple : {@code throw new RessourceIntrouvableException("Utilisateur", id);}
 * produit le message « Utilisateur introuvable avec l'identifiant 12. »
 */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }

    public RessourceIntrouvableException(String ressource, Object identifiant) {
        super(ressource + " introuvable avec l'identifiant " + identifiant + ".");
    }
}
