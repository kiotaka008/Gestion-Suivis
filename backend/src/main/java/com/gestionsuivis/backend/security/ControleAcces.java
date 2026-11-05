package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Règles d'autorisation métier, appliquées dans les services (jamais seulement
 * dans les contrôleurs ni dans React). Toutes les données sont d'abord limitées à
 * l'organisation de l'utilisateur ({@code @TenantId} + RLS) ; à l'intérieur :
 * <ul>
 *   <li><b>ADMIN</b> : toutes les données de l'organisation ;</li>
 *   <li><b>MANAGER</b> : consultation de tous les projets, activités et utilisateurs de
 *       l'organisation, création de projets, gestion des projets dont il est chef ;</li>
 *   <li><b>EMPLOYE</b> ({@code member}) : projets dont il est chef ou membre ou dans lesquels
 *       une activité lui est affectée, ses propres activités (mise à jour du statut et de
 *       l'avancement).</li>
 * </ul>
 * Un chef de projet gère son projet quel que soit son rôle.
 * Un identifiant modifié dans l'URL donne 403 (« accès interdit ») ou 404 s'il
 * appartient à une autre organisation.
 */
@Component
@RequiredArgsConstructor
public class ControleAcces {

    private final ProjetRepository projets;
    private final ActiviteRepository activites;

    // ---------------------------------------------------------------- projets

    public boolean peutGererProjet(UtilisateurConnecte u, Projet projet) {
        return u.estAdmin() || memeId(u.id(), projet.getChefDeProjet());
    }

    public boolean peutVoirProjet(UtilisateurConnecte u, Projet projet) {
        return peutGererProjet(u, projet) || u.estManager()
                || projets.existsByIdAndMembresUtilisateurId(projet.getId(), u.id())
                || activites.existsByProjetIdAndResponsableId(projet.getId(), u.id());
    }

    public void verifierVoirProjet(UtilisateurConnecte u, Projet projet) {
        if (!peutVoirProjet(u, projet)) {
            throw new AccesInterditException();
        }
    }

    public void verifierGererProjet(UtilisateurConnecte u, Projet projet) {
        if (!peutGererProjet(u, projet)) {
            throw new AccesInterditException("Vous n'avez pas les droits nécessaires pour modifier ce projet.");
        }
    }

    /** Création de projet : réservée aux ADMIN et MANAGER. */
    public void verifierCreerProjet(UtilisateurConnecte u) {
        if (!(u.estAdmin() || u.estManager())) {
            throw new AccesInterditException("Seuls les administrateurs et les responsables peuvent créer un projet.");
        }
    }

    // ---------------------------------------------------------------- activités

    public boolean peutGererActivite(UtilisateurConnecte u, Activite activite) {
        return peutGererProjet(u, activite.getProjet());
    }

    public boolean estResponsableActivite(UtilisateurConnecte u, Activite activite) {
        return memeId(u.id(), activite.getResponsable());
    }

    public boolean peutVoirActivite(UtilisateurConnecte u, Activite activite) {
        return peutGererActivite(u, activite) || u.estManager() || estResponsableActivite(u, activite);
    }

    public void verifierVoirActivite(UtilisateurConnecte u, Activite activite) {
        if (!peutVoirActivite(u, activite)) {
            throw new AccesInterditException();
        }
    }

    public void verifierGererActivite(UtilisateurConnecte u, Activite activite) {
        if (!peutGererActivite(u, activite)) {
            throw new AccesInterditException("Vous n'avez pas les droits nécessaires pour gérer cette activité.");
        }
    }

    // ---------------------------------------------------------------- utilisateurs

    public boolean peutVoirUtilisateur(UtilisateurConnecte u, Utilisateur cible) {
        return u.estAdmin() || u.estManager() || u.id().equals(cible.getId());
    }

    public void verifierVoirUtilisateur(UtilisateurConnecte u, Utilisateur cible) {
        if (!peutVoirUtilisateur(u, cible)) {
            throw new AccesInterditException();
        }
    }

    public void verifierAdmin(UtilisateurConnecte u) {
        if (!u.estAdmin()) {
            throw new AccesInterditException("Cette action est réservée aux administrateurs.");
        }
    }

    public void verifierAdminOuManager(UtilisateurConnecte u) {
        if (!(u.estAdmin() || u.estManager())) {
            throw new AccesInterditException();
        }
    }

    private static boolean memeId(UUID id, Utilisateur utilisateur) {
        return utilisateur != null && id.equals(utilisateur.getId());
    }
}
