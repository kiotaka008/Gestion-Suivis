package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.projet.CreerProjetRequest;
import com.gestionsuivis.backend.dto.projet.ModifierProjetRequest;
import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.RoleMembreProjet;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.ConflitMetierException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.ProjetMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsProjet;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Projets : CRUD, chef de projet, membres ({@code project_members}), dates et
 * statuts, avec contrôle d'accès dans chaque opération ({@link ControleAcces}).
 * Le chef de projet est toujours membre du projet avec le rôle {@code owner}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository projets;
    private final ActiviteRepository activites;
    private final UtilisateurRepository utilisateurs;
    private final ProjetMapper mapper;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public PageResponse<ProjetResponse> lister(String recherche, StatutProjet statut, Priorite priorite,
                                               UUID chefId, Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        LocalDate aujourdHui = LocalDate.now(horloge);
        return PageResponse.de(projets.findAll(SpecificationsProjet.visiblesPar(u)
                        .and(SpecificationsProjet.filtres(recherche, statut, priorite, chefId)), pageable),
                projet -> mapper.versDto(projet, aujourdHui));
    }

    @Transactional(readOnly = true)
    public ProjetResponse obtenir(UUID id) {
        Projet projet = charger(id);
        controle.verifierVoirProjet(utilisateurCourant.obtenir(), projet);
        return versDto(projet);
    }

    @Transactional
    public ProjetResponse creer(CreerProjetRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierCreerProjet(u);
        verifierDates(requete.startDate(), requete.dueDate());

        Projet projet = new Projet();
        projet.setNom(requete.name().trim());
        projet.setDescription(vide(requete.description()));
        projet.setStatut(requete.status() == null ? StatutProjet.EN_COURS : requete.status());
        projet.setPriorite(requete.priority() == null ? Priorite.MOYENNE : requete.priority());
        projet.setDateDebut(requete.startDate());
        projet.setDateEcheance(requete.dueDate());

        Utilisateur chef = requete.ownerId() == null
                ? utilisateurs.findById(u.id()).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", u.id()))
                : utilisateurActifPourChamp(requete.ownerId(), "ownerId");
        projet.setChefDeProjet(chef);
        projet.ajouterMembre(chef, RoleMembreProjet.PROPRIETAIRE);
        Set<Utilisateur> nouveauxMembres = membresPourChamp(requete.memberIds());
        nouveauxMembres.removeIf(m -> m.getId().equals(chef.getId()));
        nouveauxMembres.forEach(m -> projet.ajouterMembre(m, RoleMembreProjet.MEMBRE));

        Projet enregistre = projets.saveAndFlush(projet);
        audit.journaliser(u.id(), "created", AuditService.PROJET, enregistre.getId());
        log.info("Projet {} créé par {}", enregistre.getId(), u.id());
        notifierMembres(enregistre, nouveauxMembres, u.id());
        if (!chef.getId().equals(u.id())) {
            notifications.notifier(chef, u.id(), TypeNotification.PROJET, "Nouveau projet",
                    "Vous êtes chef du projet « " + enregistre.getNom() + " ».");
        }
        return versDto(enregistre);
    }

    @Transactional
    public ProjetResponse modifier(UUID id, ModifierProjetRequest requete) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Projet projet = charger(id);
        controle.verifierGererProjet(u, projet);
        verifierDates(requete.startDate() != null ? requete.startDate() : projet.getDateDebut(),
                requete.dueDate() != null ? requete.dueDate() : projet.getDateEcheance());

        if (requete.name() != null) {
            projet.setNom(requete.name().trim());
        }
        if (requete.description() != null) {
            projet.setDescription(vide(requete.description()));
        }
        if (requete.status() != null) {
            projet.setStatut(requete.status());
        }
        if (requete.priority() != null) {
            projet.setPriorite(requete.priority());
        }
        if (requete.startDate() != null) {
            projet.setDateDebut(requete.startDate());
        }
        if (requete.dueDate() != null) {
            projet.setDateEcheance(requete.dueDate());
        }
        if (requete.ownerId() != null && !requete.ownerId().equals(projet.getChefDeProjet().getId())) {
            if (!u.estAdmin()) {
                throw new AccesInterditException("Seul un administrateur peut changer le chef de projet.");
            }
            changerChef(projet, utilisateurActifPourChamp(requete.ownerId(), "ownerId"), u.id());
        }
        if (requete.memberIds() != null) {
            Set<Utilisateur> membres = membresPourChamp(requete.memberIds());
            UUID chefId = projet.getChefDeProjet().getId();
            Set<UUID> anciens = new LinkedHashSet<>(projet.utilisateursMembres().stream().map(Utilisateur::getId).toList());
            Set<UUID> conserves = new LinkedHashSet<>(membres.stream().map(Utilisateur::getId).toList());
            conserves.add(chefId);
            projet.getMembres().removeIf(m -> !conserves.contains(m.getUtilisateur().getId()));
            membres.forEach(m -> projet.ajouterMembre(m, RoleMembreProjet.MEMBRE));
            notifierMembres(projet, membres.stream().filter(m -> !anciens.contains(m.getId())).toList(), u.id());
        }
        audit.journaliser(u.id(), "updated", AuditService.PROJET, id);
        return versDto(projet);
    }

    /** Suppression définitive (ADMIN) : activités et membres compris. */
    @Transactional
    public void supprimer(UUID id) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        Projet projet = charger(id);
        activites.deleteAll(activites.findByProjetId(id));
        projet.getMembres().clear();
        projets.delete(projet);
        projets.flush();
        audit.journaliser(u.id(), "deleted", AuditService.PROJET, id);
        log.info("Projet {} supprimé par {}", id, u.id());
    }

    @Transactional
    public ProjetResponse ajouterMembre(UUID id, UUID utilisateurId) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Projet projet = charger(id);
        controle.verifierGererProjet(u, projet);
        Utilisateur membre = utilisateurActifPourChamp(utilisateurId, "userId");
        if (projet.ajouterMembre(membre, RoleMembreProjet.MEMBRE)) {
            notifierMembres(projet, List.of(membre), u.id());
        }
        return versDto(projet);
    }

    @Transactional
    public ProjetResponse retirerMembre(UUID id, UUID utilisateurId) {
        Projet projet = charger(id);
        controle.verifierGererProjet(utilisateurCourant.obtenir(), projet);
        if (projet.getChefDeProjet().getId().equals(utilisateurId)) {
            throw new ConflitMetierException("Le chef de projet ne peut pas être retiré des membres.");
        }
        if (!projet.retirerMembre(utilisateurId)) {
            throw new RessourceIntrouvableException("Cet utilisateur n'est pas membre du projet.");
        }
        return versDto(projet);
    }

    // ---------------------------------------------------------------- utilitaires

    Projet charger(UUID id) {
        return projets.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Projet", id));
    }

    ProjetResponse versDto(Projet projet) {
        return mapper.versDto(projet, LocalDate.now(horloge));
    }

    /** L'ancien chef reste membre ({@code member}), le nouveau devient {@code owner}. */
    private void changerChef(Projet projet, Utilisateur chef, UUID auteurId) {
        projet.membre(projet.getChefDeProjet().getId()).ifPresent(m -> m.setRole(RoleMembreProjet.MEMBRE));
        projet.setChefDeProjet(chef);
        projet.ajouterMembre(chef, RoleMembreProjet.PROPRIETAIRE);
        notifications.notifier(chef, auteurId, TypeNotification.PROJET, "Chef de projet",
                "Vous êtes désormais chef du projet « " + projet.getNom() + " ».");
    }

    private Utilisateur utilisateurActifPourChamp(UUID id, String champ) {
        Utilisateur utilisateur = utilisateurs.findById(id)
                .orElseThrow(() -> new RequeteInvalideException(champ, "Utilisateur introuvable."));
        if (!utilisateur.isActif()) {
            throw new RequeteInvalideException(champ, "Cet utilisateur est désactivé.");
        }
        return utilisateur;
    }

    private Set<Utilisateur> membresPourChamp(List<UUID> ids) {
        Set<Utilisateur> membres = new LinkedHashSet<>();
        if (ids == null) {
            return membres;
        }
        for (UUID id : new LinkedHashSet<>(ids)) {
            if (id != null) {
                membres.add(utilisateurActifPourChamp(id, "memberIds"));
            }
        }
        return membres;
    }

    private void notifierMembres(Projet projet, Collection<Utilisateur> membres, UUID auteurId) {
        for (Utilisateur membre : membres) {
            notifications.notifier(membre, auteurId, TypeNotification.PROJET, "Ajout à un projet",
                    "Vous avez été ajouté au projet « " + projet.getNom() + " ».");
        }
    }

    static void verifierDates(LocalDate debut, LocalDate echeance) {
        if (debut != null && echeance != null && echeance.isBefore(debut)) {
            throw new RequeteInvalideException("dueDate", "L'échéance doit être postérieure ou égale à la date de début.");
        }
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
