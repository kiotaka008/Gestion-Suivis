package com.gestionsuivis.backend.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Accès à l'utilisateur authentifié de la requête courante, pour les services.
 */
@Component
public class UtilisateurCourant {

    public UtilisateurConnecte obtenir() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof UtilisateurConnecte utilisateur) {
            return utilisateur;
        }
        throw new AuthenticationCredentialsNotFoundException("Utilisateur non authentifié.");
    }
}
