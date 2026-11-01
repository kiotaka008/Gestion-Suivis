package com.gestionsuivis.backend.service;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.projet.CreerProjetRequest;
import com.gestionsuivis.backend.dto.projet.ModifierProjetRequest;
import com.gestionsuivis.backend.dto.projet.ProjetResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Departement;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.AccesInterditException;
import com.gestionsuivis.backend.exception.RequeteInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.mapper.ProjetMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.CommentaireRepository;
import com.gestionsuivis.backend.repository.DepartementRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsProjet;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Projets : CRUD, chef de projet, membres, dates et statuts, avec contrôle
 * d'accès dans chaque opération ({@link ControleAcces}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository projets;
    private final ActiviteRepository activites;
    private final CommentaireRepository commentaires;
    private final UtilisateurRepository utilisateurs;
    private final DepartementRepository departements;
    private final ProjetMapper mapper;
    private final CalculAvancement avancement;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final NotificationService notifications;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public PageResponse<ProjetResponse> lister(String recherche, StatutProjet statut, Priorite priorite,
                                               UUID departementId, UUID chefId, Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Page<Projet> page = projets.findAll(SpecificationsProjet.visiblesPar(u)
                .and(SpecificationsProjet.filtres(recherche, statut, priorite, departementId, chefId)), pageable);
        Map<UUID, Integer> avancements = avancement.parProjet(page.map(Projet::getId).getContent());
        LocalDate aujourdHui = LocalDate.now(horloge);
        return PageResponse.de(page, projet ->
                mapper.versDto(projet, avancements.getOrDefault(projet.getId(), 0), aujourdHui));
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
        projet.setPriorite(requete.priority());
        projet.setDateDebut(requete.startDate());
        projet.setDateEcheance(requete.dueDate());
        projet.setDepartement(departementPourCreation(u, requete.departmentId()));

        Utilisateur chef = requete.ownerId() == null
                ? utilisateurs.getReferenceById(u.id())
                : utilisateurActifPourChamp(requete.ownerId(), "ownerId");
        if (u.estManager() && !chef.getId().equals(u.id())
                && (chef.getDepartement() == null || !u.estDansDepartement(chef.getDepartement().getId()))) {
            throw new RequeteInvalideException("ownerId", "Le chef de projet doit appartenir à votre département.");
        }
        projet.setChefDeProjet(chef);
        Set<Utilisateur> nouveauxMembres = membresPourChamp(requete.memberIds());
        projet.getMembres().addAll(nouveauxMembres);

        Projet enregistre = projets.save(projet);
        log.info("Projet {} créé par {}", enregistre.getId(), u.id());
        notifierMembres(enregistre, nouveauxMembres, u.id());
        if (!chef.getId().equals(u.id())) {
            notifications.notifier(chef, u.id(), TypeNotification.PROJET, "Nouveau projet",
                    "Vous êtes chef du projet « " + enregistre.getNom() + " ».", enregistre.getId(), null);
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
        if (requete.departmentId() != null) {
            if (!u.estAdmin()) {
                throw new AccesInterditException("Seul un administrateur peut changer le département d'un projet.");
            }
            projet.setDepartement(departements.findById(requete.departmentId())
                    .orElseThrow(() -> new RequeteInvalideException("departmentId", "Département introuvable.")));
        }
        if (requete.ownerId() != null && !requete.ownerId().equals(projet.getChefDeProjet().getId())) {
            boolean responsableDuDepartement = u.estManager() && projet.getDepartement() != null
                    && u.estDansDepartement(projet.getDepartement().getId());
            if (!(u.estAdmin() || responsableDuDepartement)) {
                throw new AccesInterditException(
                        "Seuls un administrateur ou le responsable du département peuvent changer le chef de projet.");
            }
            Utilisateur chef = utilisateurActifPourChamp(requete.ownerId(), "ownerId");
            projet.setChefDeProjet(chef);
            notifications.notifier(chef, u.id(), TypeNotification.PROJET, "Chef de projet",
                    "Vous êtes désormais chef du projet « " + projet.getNom() + " ».", projet.getId(), null);
        }
        if (requete.memberIds() != null) {
            Set<Utilisateur> membres = membresPourChamp(requete.memberIds());
            Set<UUID> anciens = new LinkedHashSet<>(projet.getMembres().stream().map(Utilisateur::getId).toList());
            projet.getMembres().clear();
            projet.getMembres().addAll(membres);
            notifierMembres(projet, membres.stream().filter(m -> !anciens.contains(m.getId())).toList(), u.id());
        }
        return versDto(projet);
    }

    /** Suppression définitive (ADMIN) : activités, commentaires et membres compris. */
    @Transactional
    public void supprimer(UUID id) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        controle.verifierAdmin(u);
        Projet projet = charger(id);
        List<Activite> activitesDuProjet = activites.findByProjetId(id);
        List<UUID> idsActivites = activitesDuProjet.stream().map(Activite::getId).toList();
        if (!idsActivites.isEmpty()) {
            commentaires.supprimerParActivites(idsActivites);
            activites.deleteAll(activitesDuProjet);
        }
        projet.getMembres().clear();
        projets.delete(projet);
        projets.flush();
        log.info("Projet {} supprimé par {}", id, u.id());
    }

    @Transactional
    public ProjetResponse ajouterMembre(UUID id, UUID utilisateurId) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        Projet projet = charger(id);
        controle.verifierGererProjet(u, projet);
        Utilisateur membre = utilisateurActifPourChamp(utilisateurId, "userId");
        if (projet.getMembres().add(membre)) {
            notifierMembres(projet, List.of(membre), u.id());
        }
        return versDto(projet);
    }

    @Transactional
    public ProjetResponse retirerMembre(UUID id, UUID utilisateurId) {
        Projet projet = charger(id);
        controle.verifierGererProjet(utilisateurCourant.obtenir(), projet);
        boolean retire = projet.getMembres().removeIf(m -> m.getId().equals(utilisateurId));
        if (!retire) {
            throw new RessourceIntrouvableException("Cet utilisateur n'est pas membre du projet.");
        }
        return versDto(projet);
    }

    // ---------------------------------------------------------------- utilitaires

    Projet charger(UUID id) {
        return projets.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Projet", id));
    }

    ProjetResponse versDto(Projet projet) {
        return mapper.versDto(projet, avancement.pourProjet(projet.getId()), LocalDate.now(horloge));
    }

    private Departement departementPourCreation(UtilisateurConnecte u, UUID departementId) {
        if (u.estManager()) {
            if (departementId != null && !u.estDansDepartement(departementId)) {
                throw new RequeteInvalideException("departmentId",
                        "Un responsable ne peut créer des projets que dans son département.");
            }
            return u.departementId() == null ? null : departements.getReferenceById(u.departementId());
        }
        if (departementId == null) {
            return null;
        }
        return departements.findById(departementId)
                .orElseThrow(() -> new RequeteInvalideException("departmentId", "Département introuvable."));
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

    private void notifierMembres(Projet projet, java.util.Collection<Utilisateur> membres, UUID auteurId) {
        for (Utilisateur membre : membres) {
            notifications.notifier(membre, auteurId, TypeNotification.PROJET, "Ajout à un projet",
                    "Vous avez été ajouté au projet « " + projet.getNom() + " ».", projet.getId(), null);
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
