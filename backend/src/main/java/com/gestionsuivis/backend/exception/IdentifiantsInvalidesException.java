package com.gestionsuivis.backend.exception;

/**
 * Échec de connexion (email inconnu, mot de passe faux ou compte désactivé).
 * Le message est volontairement identique dans tous les cas (HTTP 401) pour ne
 * pas révéler l'existence d'un compte.
 */
public class IdentifiantsInvalidesException extends RuntimeException {

    public IdentifiantsInvalidesException() {
        super(MessagesErreur.IDENTIFIANTS_INVALIDES);
    }
}
