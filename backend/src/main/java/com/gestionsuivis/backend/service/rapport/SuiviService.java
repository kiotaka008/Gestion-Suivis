package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.suivi.IndicateurRisqueResponse;
import com.gestionsuivis.backend.dto.suivi.TacheNonAccomplieResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.exception.ParametreInvalideException;
import com.gestionsuivis.backend.exception.RessourceIntrouvableException;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsActivite;
import com.gestionsuivis.backend.repository.SpecificationsProjet;
import com.gestionsuivis.backend.security.ControleAcces;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import com.gestionsuivis.backend.service.CalculAvancement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Données de suivi structurées destinées au module IA ({@code /api/suivi/*}) :
 * tâches non accomplies, tâches sans progression et indicateurs de risque.
 * Les mêmes règles de visibilité que le reste de l'API s'appliquent ; l'IA ne
 * fait que rédiger les synthèses à partir de ces données.
 */
@Service
@RequiredArgsConstructor
public class SuiviService {

    public static final int JOURS_SANS_PROGRESSION_PAR_DEFAUT = 7;

    private final ActiviteRepository activites;
    private final ProjetRepository projets;
    private final CalculAvancement avancement;
    private final ControleAcces controle;
    private final UtilisateurCourant utilisateurCourant;
    private final Clock horloge;

    /** Activités non terminées dont l'échéance est dépassée (ou au statut EN_RETARD). */
    @Transactional(readOnly = true)
    public PageResponse<TacheNonAccomplieResponse> tachesNonAccomplies(UUID projetId, UUID responsableId,
                                                                       Pageable pageable) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        LocalDate aujourdHui = LocalDate.now(horloge);
        verifierProjet(u, projetId);
        return PageResponse.de(activites.findAll(SpecificationsActivite.visiblesPar(u)
                        .and(SpecificationsActivite.filtres(null, null, null, projetId, responsableId, true, aujourdHui)),
                pageable), a -> versDto(a, "overdue", aujourdHui));
    }

    /**
     * Activités non terminées sans progression : avancement nul ({@code not_started})
     * ou non modifiées depuis {@code jours} jours ({@code no_progress}).
     */
    @Transactional(readOnly = true)
    public PageResponse<TacheNonAccomplieResponse> tachesSansProgression(Integer jours, UUID projetId,
                                                                         Pageable pageable) {
        int seuilJours = jours == null ? JOURS_SANS_PROGRESSION_PAR_DEFAUT : jours;
        if (seuilJours < 1 || seuilJours > 365) {
            throw new ParametreInvalideException("Le paramètre 'days' doit être compris entre 1 et 365.");
        }
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        LocalDate aujourdHui = LocalDate.now(horloge);
        verifierProjet(u, projetId);
        Instant seuil = horloge.instant().minus(seuilJours, ChronoUnit.DAYS);
        Specification<Activite> sansProgression = (racine, requete, cb) -> cb.and(
                cb.notEqual(racine.get("statut"), StatutActivite.TERMINE),
                cb.or(cb.equal(racine.get("avancement"), 0), cb.lessThan(racine.get("dateModification"), seuil)));
        return PageResponse.de(activites.findAll(SpecificationsActivite.visiblesPar(u).and(sansProgression)
                        .and(SpecificationsActivite.filtres(null, null, null, projetId, null, null, aujourdHui)), pageable),
                a -> versDto(a, a.getAvancement() == 0 ? "not_started" : "no_progress", aujourdHui));
    }

    /** Indicateurs de risque des projets visibles non clos (ou d'un projet précis), du plus risqué au moins risqué. */
    @Transactional(readOnly = true)
    public List<IndicateurRisqueResponse> indicateursRisque(UUID projetId) {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        LocalDate aujourdHui = LocalDate.now(horloge);
        List<Projet> cibles;
        if (projetId != null) {
            Projet projet = projets.findById(projetId).orElseThrow(() -> new RessourceIntrouvableException("Projet", projetId));
            controle.verifierVoirProjet(u, projet);
            cibles = List.of(projet);
        } else {
            cibles = projets.findAll(SpecificationsProjet.visiblesPar(u)).stream()
                    .filter(p -> !p.getStatut().estClos())
                    .toList();
        }
        List<UUID> ids = cibles.stream().map(Projet::getId).toList();
        Map<UUID, Integer> avancements = avancement.parProjet(ids);
        Map<UUID, List<Activite>> activitesParProjet = ids.isEmpty() ? Map.of()
                : activites.findAll(SpecificationsActivite.visiblesPar(u)
                        .and((racine, requete, cb) -> racine.get("projet").get("id").in(ids)))
                .stream().collect(Collectors.groupingBy(a -> a.getProjet().getId()));
        return cibles.stream()
                .map(p -> EvaluationRisque.evaluer(p, avancements.getOrDefault(p.getId(), 0),
                        activitesParProjet.getOrDefault(p.getId(), List.of()), aujourdHui))
                .sorted(Comparator.comparingInt((IndicateurRisqueResponse r) -> rang(r.riskLevel())).reversed()
                        .thenComparing(IndicateurRisqueResponse::projectName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void verifierProjet(UtilisateurConnecte u, UUID projetId) {
        if (projetId != null) {
            Projet projet = projets.findById(projetId).orElseThrow(() -> new RessourceIntrouvableException("Projet", projetId));
            controle.verifierVoirProjet(u, projet);
        }
    }

    private TacheNonAccomplieResponse versDto(Activite a, String raison, LocalDate aujourdHui) {
        Utilisateur responsable = a.getResponsable();
        Long joursRetard = a.getDateEcheance() != null && a.getDateEcheance().isBefore(aujourdHui)
                ? ChronoUnit.DAYS.between(a.getDateEcheance(), aujourdHui) : null;
        long joursSansMaj = a.getDateModification() == null ? 0
                : ChronoUnit.DAYS.between(a.getDateModification().atZone(horloge.getZone()).toLocalDate(), aujourdHui);
        return new TacheNonAccomplieResponse(a.getId(), a.getTitre(), a.getProjet().getId(), a.getProjet().getNom(),
                responsable == null ? null : responsable.getId(), responsable == null ? null : responsable.getNom(),
                a.getStatut(), a.getPriorite(), a.getAvancement(), a.getDateEcheance(), joursRetard, joursSansMaj,
                a.getHeuresPrevues(), a.getHeuresReelles(), raison, a.getDateModification());
    }

    private static int rang(String niveau) {
        return switch (niveau) {
            case "critical" -> 3;
            case "high" -> 2;
            case "medium" -> 1;
            default -> 0;
        };
    }
}
