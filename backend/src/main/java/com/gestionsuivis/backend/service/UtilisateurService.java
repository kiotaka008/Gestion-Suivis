package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.utilisateur.CreerUtilisateurRequest;
import com.gestionsuivis.backend.dto.utilisateur.ModifierProfilRequest;
import com.gestionsuivis.backend.dto.utilisateur.ModifierUtilisateurRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.CommentaireRepository;
import com.gestionsuivis.backend.repository.ConversationIaRepository;
import com.gestionsuivis.backend.repository.DepartementRepository;
import com.gestionsuivis.backend.repository.NotificationRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsUtilisateur;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Gestion des utilisateurs : consultation selon le périmètre, création,
 * modification, activation, désactivation et suppression (ADMIN), profil personnel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurs;
    private final DepartementRepository departements;
    private final ProjetRepository projets;
    private final ActiviteRepository activites;
    private final CommentaireRepository commentaires;
    private final ConversationIaRepository conversations;
    private final NotificationRepository notifications;
    private final PasswordEncoder encodeur;
    private final UtilisateurMapper mapper;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;

    @Transactional(readOnly = true)
    public PageResponse<UtilisateurResponse> lister(String recherche, Role role, Boolean actif, UUID departementId,
                                                    Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        if (u.estEmploye()) {
            throw new AccesInterditException("La liste des utilisateurs est réservée aux administrateurs et responsables.");
        }
        return PageResponse.de(utilisateurs.findAll(
                SpecificationsUtilisateur.visiblesPar(u)
                        .and(SpecificationsUtilisateur.filtres(recherche, role, actif, departementId)),
                pageable), mapper::versDto);
    }

    @Transactional(readOnly = true)
    public UtilisateurResponse obtenir(UUID id) {
        Utilisateur cible = charger(id);
        controle.verifierVoirUtilisateur(utilisateurCourant.obtenir(), cible);
        return mapper.versDto(cible);
    }

    @Transactional
    public UtilisateurResponse creer(CreerUtilisateurRequest requete) {
        controle.verifierAdmin(utilisateurCourant.obtenir());
        String email = AuthService.normaliser(requete.email());
        if (utilisateurs.existsByEmailIgnoreCase(email)) {
            throw new ConflitMetierException("Cet email est déjà utilisé.");
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(requete.name().trim());
        utilisateur.setEmail(email);
        utilisateur.setRole(requete.role());
        utilisateur.setMotDePasseHash(encodeur.encode(requete.password()));
        utilisateur.setActif(requete.isActive() == null || requete.isActive());
        utilisateur.setDepartement(requete.departmentId() == null ? null : departementPourChamp(requete.departmentId()));
        Utilisateur enregistre = utilisateurs.save(utilisateur);
        log.info("Utilisateur {} créé avec le rôle {}", enregistre.getId(), enregistre.getRole());
        return mapper.versDto(enregistre);
    }

    @Transactional
    public UtilisateurResponse modifier(UUID id, ModifierUtilisateurRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        Utilisateur utilisateur = charger(id);
        if (requete.name() != null) {
            utilisateur.setNom(requete.name().trim());
        }
        if (requete.email() != null) {
            changerEmail(utilisateur, requete.email());
        }
        if (requete.role() != null && requete.role() != utilisateur.getRole()) {
            changerRole(utilisateur, requete.role());
        }
        if (requete.departmentId() != null) {
            utilisateur.setDepartement(departementPourChamp(requete.departmentId()));
        }
        if (requete.avatarUrl() != null) {
            utilisateur.setAvatarUrl(vide(requete.avatarUrl()));
        }
        if (requete.bio() != null) {
            utilisateur.setBio(vide(requete.bio()));
        }
        return mapper.versDto(utilisateur);
    }

    @Transactional
    public void activer(UUID id) {
        controle.verifierAdmin(utilisateurCourant.obtenir());
        charger(id).setActif(true);
    }

    @Transactional
    public void desactiver(UUID id) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        if (u.id().equals(id)) {
            throw new ConflitMetierException("Vous ne pouvez pas désactiver votre propre compte.");
        }
        Utilisateur utilisateur = charger(id);
        if (utilisateur.getRole() == Role.ADMIN && utilisateur.isActif() && dernierAdministrateurActif()) {
            throw new ConflitMetierException("Impossible de désactiver le dernier administrateur actif.");
        }
        utilisateur.setActif(false);
    }

    /**
     * Suppression définitive (ADMIN). Refusée si l'utilisateur est encore lié à des
     * projets, activités, commentaires, départements ou conversations : il faut
     * alors le désactiver.
     */
    @Transactional
    public void supprimer(UUID id) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        if (u.id().equals(id)) {
            throw new ConflitMetierException("Vous ne pouvez pas supprimer votre propre compte.");
        }
        Utilisateur utilisateur = charger(id);
        if (utilisateur.getRole() == Role.ADMIN && utilisateur.isActif() && dernierAdministrateurActif()) {
            throw new ConflitMetierException("Impossible de supprimer le dernier administrateur actif.");
        }
        if (projets.existsByChefDeProjetId(id) || activites.countByResponsableId(id) > 0
                || activites.existsByCreateurId(id) || commentaires.existsByAuteurId(id)
                || departements.existsByResponsableId(id) || conversations.existsByProprietaireId(id)) {
            throw new ConflitMetierException("Cet utilisateur est lié à des projets, activités, commentaires "
                    + "ou conversations : désactivez-le plutôt que de le supprimer.");
        }
        projets.findByMembresId(id).forEach(projet -> projet.getMembres().remove(utilisateur));
        notifications.supprimerParDestinataire(id);
        utilisateurs.delete(utilisateur);
        utilisateurs.flush();
        log.info("Utilisateur {} supprimé", id);
    }

    // ---------------------------------------------------------------- profil

    @Transactional
    public UtilisateurResponse modifierProfil(ModifierProfilRequest requete) {
        Utilisateur utilisateur = charger(utilisateurCourant.obtenir().id());
        if (requete.name() != null) {
            utilisateur.setNom(requete.name().trim());
        }
        if (requete.email() != null) {
            changerEmail(utilisateur, requete.email());
        }
        if (requete.bio() != null) {
            utilisateur.setBio(vide(requete.bio()));
        }
        if (requete.avatarUrl() != null) {
            utilisateur.setAvatarUrl(vide(requete.avatarUrl()));
        }
        return mapper.versDto(utilisateur);
    }

    // ---------------------------------------------------------------- utilitaires

    Utilisateur charger(UUID id) {
        return utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", id));
    }

    private void changerEmail(Utilisateur utilisateur, String nouvelEmail) {
        String email = AuthService.normaliser(nouvelEmail);
        if (!email.equalsIgnoreCase(utilisateur.getEmail())
                && utilisateurs.existsByEmailIgnoreCaseAndIdNot(email, utilisateur.getId())) {
            throw new ConflitMetierException("Cet email est déjà utilisé.");
        }
        utilisateur.setEmail(email);
    }

    private void changerRole(Utilisateur utilisateur, Role nouveauRole) {
        if (utilisateur.getRole() == Role.ADMIN && utilisateur.isActif() && dernierAdministrateurActif()) {
            throw new ConflitMetierException("Impossible de retirer le rôle du dernier administrateur actif.");
        }
        if (nouveauRole == Role.EMPLOYE && departements.existsByResponsableId(utilisateur.getId())) {
            throw new ConflitMetierException(
                    "Cet utilisateur est responsable d'un département : désignez d'abord un autre responsable.");
        }
        utilisateur.setRole(nouveauRole);
    }

    private boolean dernierAdministrateurActif() {
        return utilisateurs.countByRoleAndActifTrue(Role.ADMIN) <= 1;
    }

    private Departement departementPourChamp(UUID id) {
        return departements.findById(id)
                .orElseThrow(() -> new RequeteInvalideException("departmentId", "Département introuvable."));
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
