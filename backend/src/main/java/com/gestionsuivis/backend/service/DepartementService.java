package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.departement.CreerDepartementRequest;
import com.gestionsuivis.backend.dto.departement.DepartementResponse;
import com.gestionsuivis.backend.dto.departement.ModifierDepartementRequest;
import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Role;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.DepartementMapper;
import com.gestionsuivis.backend.mapper.UtilisateurMapper;
import com.gestionsuivis.backend.repository.DepartementRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsUtilisateur;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Départements : consultation par tout utilisateur connecté, gestion (création,
 * modification, suppression, responsable, affectation des membres) réservée aux ADMIN.
 */
@Service
@RequiredArgsConstructor
public class DepartementService {

    private final DepartementRepository departements;
    private final UtilisateurRepository utilisateurs;
    private final ProjetRepository projets;
    private final DepartementMapper mapper;
    private final UtilisateurMapper utilisateurMapper;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public PageResponse<DepartementResponse> lister(String recherche, Pageable pageable) {
        Specification<Departement> specification = recherche == null || recherche.isBlank()
                ? Specification.unrestricted()
                : (racine, requete, cb) -> cb.like(cb.lower(racine.get("nom")),
                "%" + recherche.trim().toLowerCase() + "%");
        return PageResponse.de(departements.findAll(specification, pageable), this::versDto);
    }

    @Transactional(readOnly = true)
    public DepartementResponse obtenir(UUID id) {
        return versDto(charger(id));
    }

    @Transactional
    public DepartementResponse creer(CreerDepartementRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        if (departements.existsByNomIgnoreCase(requete.name().trim())) {
            throw new ConflitMetierException("Un département porte déjà ce nom.");
        }
        Departement departement = new Departement();
        departement.setNom(requete.name().trim());
        departement.setDescription(vide(requete.description()));
        departement = departements.save(departement);
        if (requete.managerId() != null) {
            affecterResponsable(departement, utilisateurPourChamp(requete.managerId(), "managerId"), u.id());
        }
        return versDto(departement);
    }

    @Transactional
    public DepartementResponse modifier(UUID id, ModifierDepartementRequest requete) {
        controle.verifierAdmin(utilisateurCourant.obtenir());
        Departement departement = charger(id);
        if (requete.name() != null) {
            String nom = requete.name().trim();
            if (departements.existsByNomIgnoreCaseAndIdNot(nom, id)) {
                throw new ConflitMetierException("Un département porte déjà ce nom.");
            }
            departement.setNom(nom);
        }
        if (requete.description() != null) {
            departement.setDescription(vide(requete.description()));
        }
        return versDto(departement);
    }

    @Transactional
    public void supprimer(UUID id) {
        controle.verifierAdmin(utilisateurCourant.obtenir());
        Departement departement = charger(id);
        if (utilisateurs.countByDepartementId(id) > 0 || projets.countByDepartementId(id) > 0) {
            throw new ConflitMetierException(
                    "Ce département contient encore des utilisateurs ou des projets : réaffectez-les d'abord.");
        }
        departements.delete(departement);
    }

    @Transactional
    public DepartementResponse definirResponsable(UUID id, UUID utilisateurId) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        Departement departement = charger(id);
        affecterResponsable(departement, utilisateurPourChamp(utilisateurId, "userId"), u.id());
        return versDto(departement);
    }

    @Transactional
    public DepartementResponse retirerResponsable(UUID id) {
        controle.verifierAdmin(utilisateurCourant.obtenir());
        Departement departement = charger(id);
        departement.setResponsable(null);
        return versDto(departement);
    }

    @Transactional(readOnly = true)
    public PageResponse<UtilisateurResponse> listerMembres(UUID id, Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        charger(id);
        if (!(u.estAdmin() || (u.estManager() && u.estDansDepartement(id)))) {
            throw new AccesInterditException();
        }
        return PageResponse.de(utilisateurs.findAll(SpecificationsUtilisateur.filtres(null, null, null, id), pageable),
                utilisateurMapper::versDto);
    }

    @Transactional
    public void ajouterMembre(UUID id, UUID utilisateurId) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        Departement departement = charger(id);
        Utilisateur membre = utilisateurPourChamp(utilisateurId, "userId");
        if (membre.getDepartement() != null && departements.existsByResponsableId(membre.getId())
                && !membre.getDepartement().getId().equals(id)) {
            throw new ConflitMetierException(
                    "Cet utilisateur est responsable d'un autre département : désignez d'abord un autre responsable.");
        }
        membre.setDepartement(departement);
        notifications.notifier(membre, u.id(), TypeNotification.UTILISATEUR, "Affectation à un département",
                "Vous avez été affecté au département « " + departement.getNom() + " ».", null, null);
    }

    @Transactional
    public void retirerMembre(UUID id, UUID utilisateurId) {
        controle.verifierAdmin(utilisateurCourant.obtenir());
        Departement departement = charger(id);
        Utilisateur membre = utilisateurs.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", utilisateurId));
        if (membre.getDepartement() == null || !membre.getDepartement().getId().equals(id)) {
            throw new RessourceIntrouvableException("Cet utilisateur n'appartient pas à ce département.");
        }
        if (departement.getResponsable() != null && departement.getResponsable().getId().equals(utilisateurId)) {
            throw new ConflitMetierException("Retirez d'abord cet utilisateur de la responsabilité du département.");
        }
        membre.setDepartement(null);
    }

    // ---------------------------------------------------------------- utilitaires

    private void affecterResponsable(Departement departement, Utilisateur responsable, UUID auteurId) {
        if (responsable.getRole() != Role.MANAGER) {
            throw new RequeteInvalideException("userId", "Le responsable doit avoir le rôle « manager ».");
        }
        if (!responsable.isActif()) {
            throw new RequeteInvalideException("userId", "Le responsable doit être un utilisateur actif.");
        }
        departement.setResponsable(responsable);
        responsable.setDepartement(departement);
        notifications.notifier(responsable, auteurId, TypeNotification.UTILISATEUR, "Responsable de département",
                "Vous êtes désormais responsable du département « " + departement.getNom() + " ».", null, null);
    }

    private Departement charger(UUID id) {
        return departements.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Département", id));
    }

    private Utilisateur utilisateurPourChamp(UUID id, String champ) {
        return utilisateurs.findById(id)
                .orElseThrow(() -> new RequeteInvalideException(champ, "Utilisateur introuvable."));
    }

    private DepartementResponse versDto(Departement departement) {
        return mapper.versDto(departement, utilisateurs.countByDepartementId(departement.getId()));
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
