package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.activite.CreerActiviteRequest;
import com.gestionsuivis.backend.dto.activite.ModifierActiviteRequest;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.RoleMembreProjet;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.ActiviteMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsActivite;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Activités et tâches : CRUD, affectation, priorités, statuts et dates. La personne
 * affectée sans droit de gestion ne peut modifier que le statut et l'avancement de
 * ses propres activités. Chaque changement recalcule l'avancement du projet.
 */
@Service
@RequiredArgsConstructor
public class ActiviteService {

    private final ActiviteRepository activites;
    private final ProjetRepository projets;
    private final UtilisateurRepository utilisateurs;
    private final ActiviteMapper mapper;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final NotificationService notifications;
    private final CalculAvancement avancement;
    private final AuditService audit;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public PageResponse<ActiviteResponse> lister(String recherche, StatutActivite statut, Priorite priorite,
                                                 UUID projetId, UUID responsableId, Boolean enRetard,
                                                 Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        LocalDate aujourdHui = LocalDate.now(horloge);
        if (projetId != null) {
            Projet projet = projets.findById(projetId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Projet", projetId));
            controle.verifierVoirProjet(u, projet);
        }
        return PageResponse.de(activites.findAll(SpecificationsActivite.visiblesPar(u)
                        .and(SpecificationsActivite.filtres(recherche, statut, priorite, projetId, responsableId,
                                enRetard, aujourdHui)), pageable),
                activite -> mapper.versDto(activite, aujourdHui));
    }

    @Transactional(readOnly = true)
    public ActiviteResponse obtenir(UUID id) {
        Activite activite = charger(id);
        controle.verifierVoirActivite(utilisateurCourant.obtenir(), activite);
        return versDto(activite);
    }

    @Transactional
    public ActiviteResponse creer(CreerActiviteRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Projet projet = projets.findById(requete.projectId())
                .orElseThrow(() -> new RequeteInvalideException("projectId", "Projet introuvable."));
        if (!controle.peutGererProjet(u, projet)) {
            throw new AccesInterditException("Vous n'avez pas les droits nécessaires pour ajouter une activité à ce projet.");
        }
        verifierProjetOuvert(projet);
        ProjetService.verifierDates(requete.startDate(), requete.dueDate());

        Activite activite = new Activite();
        activite.setProjet(projet);
        activite.setTitre(requete.title().trim());
        activite.setDescription(vide(requete.description()));
        activite.setPriorite(requete.priority() == null ? Priorite.MOYENNE : requete.priority());
        activite.setDateDebut(requete.startDate());
        activite.setDateEcheance(requete.dueDate());
        activite.setAvancement(requete.progress() == null ? 0 : requete.progress());
        appliquerStatut(activite, requete.status() == null ? StatutActivite.A_FAIRE : requete.status());
        Utilisateur responsable = requete.assigneeId() == null ? null : utilisateurActifPourChamp(requete.assigneeId());
        Activite enregistree = activites.saveAndFlush(activite);
        if (responsable != null) {
            affecter(enregistree, responsable, u.id());
        }
        avancement.recalculer(projet);
        audit.journaliser(u.id(), "created", AuditService.ACTIVITE, enregistree.getId());
        // Visible aussi dans l'historique du projet (même après suppression de l'activité).
        audit.journaliser(u.id(), "activity_created", AuditService.PROJET, projet.getId(),
                AuditService.details(AuditService.CLE_ACTIVITE, enregistree.getId(),
                        AuditService.CLE_TITRE_ACTIVITE, enregistree.getTitre()));
        return versDto(enregistree);
    }

    @Transactional
    public ActiviteResponse modifier(UUID id, ModifierActiviteRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Activite activite = charger(id);
        boolean gestionnaire = controle.peutGererActivite(u, activite);
        if (!gestionnaire) {
            if (!controle.estResponsableActivite(u, activite)) {
                throw new AccesInterditException();
            }
            if (!requete.limiteeAuSuivi()) {
                throw new AccesInterditException("Vous pouvez uniquement modifier le statut et l'avancement "
                        + "de vos activités.");
            }
        }
        // Après les droits (403), avant toute écriture : rien n'est modifié sur un projet clos (409).
        verifierProjetOuvert(activite.getProjet());
        ProjetService.verifierDates(requete.startDate() != null ? requete.startDate() : activite.getDateDebut(),
                requete.dueDate() != null ? requete.dueDate() : activite.getDateEcheance());

        // Champs réellement modifiés (noms seulement), pour l'historique.
        List<String> champs = new ArrayList<>();
        AuditService.siModifie(champs, "title", activite.getTitre(),
                requete.title() == null ? null : requete.title().trim());
        if (requete.description() != null
                && !Objects.equals(activite.getDescription(), vide(requete.description()))) {
            champs.add("description");
        }
        AuditService.siModifie(champs, "priority", activite.getPriorite(), requete.priority());
        AuditService.siModifie(champs, "startDate", activite.getDateDebut(), requete.startDate());
        AuditService.siModifie(champs, "dueDate", activite.getDateEcheance(), requete.dueDate());
        AuditService.siModifie(champs, "progress", activite.getAvancement(), requete.progress());
        AuditService.siModifie(champs, "assigneeId",
                activite.getResponsable() == null ? null : activite.getResponsable().getId(), requete.assigneeId());
        AuditService.siModifie(champs, "status", activite.getStatut(), requete.status());
        boolean statutModifie = champs.contains("status");

        if (requete.title() != null) {
            activite.setTitre(requete.title().trim());
        }
        if (requete.description() != null) {
            activite.setDescription(vide(requete.description()));
        }
        if (requete.priority() != null) {
            activite.setPriorite(requete.priority());
        }
        if (requete.startDate() != null) {
            activite.setDateDebut(requete.startDate());
        }
        if (requete.dueDate() != null) {
            activite.setDateEcheance(requete.dueDate());
        }
        if (requete.progress() != null) {
            activite.setAvancement(requete.progress());
        }
        if (requete.assigneeId() != null && (activite.getResponsable() == null
                || !requete.assigneeId().equals(activite.getResponsable().getId()))) {
            affecter(activite, utilisateurActifPourChamp(requete.assigneeId()), u.id());
        }
        if (requete.status() != null && requete.status() != activite.getStatut()) {
            boolean termineeMaintenant = requete.status() == StatutActivite.TERMINE;
            appliquerStatut(activite, requete.status());
            if (termineeMaintenant) {
                notifierFin(activite, u.id());
            }
        }
        avancement.recalculer(activite.getProjet());
        if (!champs.isEmpty()) {
            Map<String, Object> details = new HashMap<>();
            details.put(AuditService.CLE_CHAMPS, champs);
            if (statutModifie) {
                details.put(AuditService.CLE_STATUT, activite.getStatut().json());
            }
            audit.journaliser(u.id(), "updated", AuditService.ACTIVITE, id, details);
        }
        return versDto(activite);
    }

    @Transactional
    public void supprimer(UUID id) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Activite activite = charger(id);
        controle.verifierGererActivite(u, activite);
        Projet projet = activite.getProjet();
        verifierProjetOuvert(projet);
        String titre = activite.getTitre();
        activites.delete(activite);
        activites.flush();
        avancement.recalculer(projet);
        audit.journaliser(u.id(), "deleted", AuditService.ACTIVITE, id);
        audit.journaliser(u.id(), "activity_deleted", AuditService.PROJET, projet.getId(),
                AuditService.details(AuditService.CLE_ACTIVITE, id, AuditService.CLE_TITRE_ACTIVITE, titre));
    }

    // ---------------------------------------------------------------- utilitaires

    /**
     * Un projet terminé ou archivé est en lecture seule pour ses activités : ni création,
     * ni modification, ni suppression (409). Il faut d'abord le rouvrir.
     */
    private static void verifierProjetOuvert(Projet projet) {
        if (projet.getStatut().estClos()) {
            throw new ConflitMetierException("Impossible d'ajouter une activité à un projet terminé ou archivé.");
        }
    }

    private Activite charger(UUID id) {
        return activites.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Activité", id));
    }

    private ActiviteResponse versDto(Activite activite) {
        return mapper.versDto(activite, LocalDate.now(horloge));
    }

    /** Statut terminé : avancement à 100 %. */
    private static void appliquerStatut(Activite activite, StatutActivite statut) {
        activite.setStatut(statut);
        if (statut == StatutActivite.TERMINE) {
            activite.setAvancement(100);
        }
    }

    /** Affecte l'activité, ajoute la personne aux membres du projet et la notifie. */
    private void affecter(Activite activite, Utilisateur responsable, UUID auteurId) {
        activite.setResponsable(responsable);
        activite.getProjet().ajouterMembre(responsable, RoleMembreProjet.MEMBRE);
        notifications.notifier(responsable, auteurId, TypeNotification.ACTIVITE, "Nouvelle activité affectée",
                "L'activité « " + activite.getTitre() + " » du projet « " + activite.getProjet().getNom()
                        + " » vous a été affectée.");
    }

    private void notifierFin(Activite activite, UUID auteurId) {
        notifications.notifier(activite.getProjet().getChefDeProjet(), auteurId, TypeNotification.ACTIVITE,
                "Activité terminée", "L'activité « " + activite.getTitre() + " » du projet « "
                        + activite.getProjet().getNom() + " » est terminée.");
    }

    private Utilisateur utilisateurActifPourChamp(UUID id) {
        Utilisateur utilisateur = utilisateurs.findById(id)
                .orElseThrow(() -> new RequeteInvalideException("assigneeId", "Utilisateur introuvable."));
        if (!utilisateur.isActif()) {
            throw new RequeteInvalideException("assigneeId", "Cet utilisateur est désactivé.");
        }
        return utilisateur;
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
