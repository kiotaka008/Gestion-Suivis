package com.gestionsuivis.backend.mapper;

import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Utilisateur;
import org.springframework.stereotype.Component;

/**
 * Conversion {@link Utilisateur} → {@link UtilisateurResponse}. Le hash du mot de
 * passe n'est jamais copié. À appeler dans une transaction (département paresseux).
 */
@Component
public class UtilisateurMapper {

    public UtilisateurResponse versDto(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        Departement departement = utilisateur.getDepartement();
        return new UtilisateurResponse(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getNom(),
                utilisateur.getRole(),
                utilisateur.getAvatarUrl(),
                utilisateur.isActif(),
                utilisateur.getDateCreation(),
                departement == null ? null : departement.getId(),
                departement == null ? null : departement.getNom(),
                utilisateur.getBio());
    }
}
