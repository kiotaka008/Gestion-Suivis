package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.activite.ActiviteResponse;
import com.gestionsuivis.backend.dto.activite.CommentaireResponse;
import com.gestionsuivis.backend.dto.activite.CreerActiviteRequest;
import com.gestionsuivis.backend.dto.activite.CreerCommentaireRequest;
import com.gestionsuivis.backend.dto.activite.ModifierActiviteRequest;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Commentaire;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.ActiviteMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.CommentaireRepository;
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
import java.util.List;
import java.util.UUID;

/**
 * Activités et tâches : CRUD, affectation, priorités, statuts, dates, heures et
 * commentaires. La personne affectée sans droit de gestion ne peut modifier que
 * le statut, l'avancement et les heures réelles de ses propres activités.
 */
@Service
@RequiredArgsConstructor
public class ActiviteService {

    private final ActiviteRepository activites;
    private final ProjetRepository projets;
    private final UtilisateurRepository utilisateurs;
    private final CommentaireRepository commentaires;
    private final ActiviteMapper mapper;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final NotificationService notifications;
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
        if (projet.getStatut().estClos()) {
            throw new ConflitMetierException("Impossible d'ajouter une activité à un projet terminé ou annulé.");
        }
        ProjetService.verifierDates(requete.startDate(), requete.dueDate());

        Activite activite = new Activite();
        activite.setProjet(projet);
        activite.setTitre(requete.title().trim());
        activite.setDescription(vide(requete.description()));
        activite.setPriorite(requete.priority() == null ? Priorite.MOYENNE : requete.priority());
        activite.setDateDebut(requete.startDate());
        activite.setDateEcheance(requete.dueDate());
        activite.setHeuresPrevues(requete.plannedHours());
        activite.setAvancement(requete.progress() == null ? 0 : requete.progress());
        activite.setCreateur(utilisateurs.getReferenceById(u.id()));
        appliquerStatut(activite, requete.status() == null ? StatutActivite.A_FAIRE : requete.status());
        Utilisateur responsable = requete.assigneeId() == null ? null : utilisateurActifPourChamp(requete.assigneeId());
        Activite enregistree = activites.save(activite);
        if (responsable != null) {
            affecter(enregistree, responsable, u.id());
        }
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
                throw new AccesInterditException("Vous pouvez uniquement modifier le statut, l'avancement "
                        + "et les heures réelles de vos activités.");
            }
        }
        ProjetService.verifierDates(requete.startDate() != null ? requete.startDate() : activite.getDateDebut(),
                requete.dueDate() != null ? requete.dueDate() : activite.getDateEcheance());

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
        if (requete.plannedHours() != null) {
            activite.setHeuresPrevues(requete.plannedHours());
        }
        if (requete.actualHours() != null) {
            activite.setHeuresReelles(requete.actualHours());
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
        return versDto(activite);
    }

    @Transactional
    public void supprimer(UUID id) {
        Activite activite = charger(id);
        controle.verifierGererActivite(utilisateurCourant.obtenir(), activite);
        commentaires.supprimerParActivites(List.of(id));
        activites.delete(activite);
    }

    // ---------------------------------------------------------------- commentaires

    @Transactional(readOnly = true)
    public PageResponse<CommentaireResponse> listerCommentaires(UUID activiteId, Pageable pageable) {
        Activite activite = charger(activiteId);
        controle.verifierVoirActivite(utilisateurCourant.obtenir(), activite);
        return PageResponse.de(commentaires.findByActiviteId(activiteId, pageable), mapper::versDto);
    }

    @Transactional
    public CommentaireResponse commenter(UUID activiteId, CreerCommentaireRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Activite activite = charger(activiteId);
        controle.verifierVoirActivite(u, activite);
        Commentaire commentaire = new Commentaire();
        commentaire.setActivite(activite);
        commentaire.setAuteur(utilisateurs.getReferenceById(u.id()));
        commentaire.setContenu(requete.content().trim());
        Commentaire enregistre = commentaires.save(commentaire);

        String message = "Nouveau commentaire sur l'activité « " + activite.getTitre() + " ».";
        notifications.notifier(activite.getResponsable(), u.id(), TypeNotification.COMMENTAIRE, "Nouveau commentaire",
                message, activite.getProjet().getId(), activite.getId());
        Utilisateur chef = activite.getProjet().getChefDeProjet();
        if (activite.getResponsable() == null || !chef.getId().equals(activite.getResponsable().getId())) {
            notifications.notifier(chef, u.id(), TypeNotification.COMMENTAIRE, "Nouveau commentaire",
                    message, activite.getProjet().getId(), activite.getId());
        }
        return mapper.versDto(enregistre);
    }

    // ---------------------------------------------------------------- utilitaires

    private Activite charger(UUID id) {
        return activites.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Activité", id));
    }

    private ActiviteResponse versDto(Activite activite) {
        return mapper.versDto(activite, LocalDate.now(horloge));
    }

    /** Statut terminé : avancement à 100 % et date de fin ; sinon la date de fin est effacée. */
    private void appliquerStatut(Activite activite, StatutActivite statut) {
        activite.setStatut(statut);
        if (statut == StatutActivite.TERMINE) {
            activite.setAvancement(100);
            activite.setDateFin(horloge.instant());
        } else {
            activite.setDateFin(null);
        }
    }

    /** Affecte l'activité, ajoute la personne aux membres du projet et la notifie. */
    private void affecter(Activite activite, Utilisateur responsable, UUID auteurId) {
        activite.setResponsable(responsable);
        activite.getProjet().getMembres().add(responsable);
        notifications.notifier(responsable, auteurId, TypeNotification.ACTIVITE, "Nouvelle activité affectée",
                "L'activité « " + activite.getTitre() + " » du projet « " + activite.getProjet().getNom()
                        + " » vous a été affectée.", activite.getProjet().getId(), activite.getId());
    }

    private void notifierFin(Activite activite, UUID auteurId) {
        notifications.notifier(activite.getProjet().getChefDeProjet(), auteurId, TypeNotification.ACTIVITE,
                "Activité terminée", "L'activité « " + activite.getTitre() + " » du projet « "
                        + activite.getProjet().getNom() + " » est terminée.",
                activite.getProjet().getId(), activite.getId());
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
