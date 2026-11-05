package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Utilisateur;
import org.springframework.stereotype.Component;

/**
 * Conversion {@link Utilisateur} → {@link UtilisateurResponse}. Le hash du mot de
 * passe n'est jamais copié.
 */
@Component
public class UtilisateurMapper {

    public UtilisateurResponse versDto(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        return new UtilisateurResponse(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getNom(),
                utilisateur.getRole(),
                utilisateur.getAvatarUrl(),
                utilisateur.isActif(),
                utilisateur.getDateCreation(),
                utilisateur.getOrganisationId(),
                utilisateur.getDerniereConnexion());
    }
}
