package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.dto.rapport.GraphiqueSeries;
import com.gestionsuivis.backend.dto.rapport.TableauBordResponse;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutActivite;
import com.gestionsuivis.backend.entity.StatutProjet;
import com.gestionsuivis.backend.mapper.ActiviteMapper;
import com.gestionsuivis.backend.mapper.ProjetMapper;
import com.gestionsuivis.backend.repository.ActiviteRepository;
import com.gestionsuivis.backend.repository.ProjetRepository;
import com.gestionsuivis.backend.repository.SpecificationsActivite;
import com.gestionsuivis.backend.repository.SpecificationsProjet;
import com.gestionsuivis.backend.security.UtilisateurCourant;
import com.gestionsuivis.backend.security.UtilisateurConnecte;
import com.gestionsuivis.backend.service.CalculAvancement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statistiques du tableau de bord, sur le périmètre de l'utilisateur connecté.
 * Les séries mensuelles et hebdomadaires sont reconstituées à partir des dates
 * de création et de fin (approximation : le statut historique n'est pas conservé).
 */
@Service
@RequiredArgsConstructor
public class TableauBordService {

    private static final int NOMBRE_MOIS = 7;
    private static final int NOMBRE_SEMAINES = 4;
    private static final int TAILLE_LISTES = 5;

    private final ProjetRepository projets;
    private final ActiviteRepository activites;
    private final ProjetMapper projetMapper;
    private final ActiviteMapper activiteMapper;
    private final CalculAvancement avancement;
    private final UtilisateurCourant utilisateurCourant;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public TableauBordResponse statistiques() {
        UtilisateurConnecte u = utilisateurCourant.obtenir();
        LocalDate aujourdHui = LocalDate.now(horloge);
        ZoneId zone = horloge.getZone();
        List<Projet> projetsVisibles = projets.findAll(SpecificationsProjet.visiblesPar(u));
        List<Activite> activitesVisibles = activites.findAll(SpecificationsActivite.visiblesPar(u));

        List<Projet> echeances = projetsVisibles.stream()
                .filter(p -> !p.getStatut().estClos() && p.getDateEcheance() != null
                        && !p.getDateEcheance().isBefore(aujourdHui))
                .sorted(Comparator.comparing(Projet::getDateEcheance))
                .limit(TAILLE_LISTES)
                .toList();
        Map<UUID, Integer> avancements = avancement.parProjet(echeances.stream().map(Projet::getId).toList());

        return new TableauBordResponse(
                projetsVisibles.size(),
                projetsVisibles.stream().filter(p -> p.getStatut() == StatutProjet.EN_COURS).count(),
                projetsVisibles.stream().filter(p -> p.getStatut() == StatutProjet.TERMINE).count(),
                projetsVisibles.stream().filter(p -> p.estEnRetard(aujourdHui)).count(),
                activitesVisibles.size(),
                activitesVisibles.stream().filter(Activite::estTerminee).count(),
                activitesVisibles.stream().filter(a -> a.estEnRetard(aujourdHui)).count(),
                evolutionProjets(projetsVisibles, aujourdHui, zone),
                Graphiques.statutsProjets(projetsVisibles, aujourdHui),
                tachesParSemaine(activitesVisibles, aujourdHui, zone),
                echeances.stream()
                        .map(p -> projetMapper.versDto(p, avancements.getOrDefault(p.getId(), 0), aujourdHui))
                        .toList(),
                activitesVisibles.stream()
                        .sorted(Comparator.comparing(Activite::getDateModification,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(TAILLE_LISTES)
                        .map(a -> activiteMapper.versDto(a, aujourdHui))
                        .toList());
    }

    /** Séries « Total », « En cours », « Terminés » (projets cumulés par mois de création). */
    private GraphiqueSeries evolutionProjets(List<Projet> projetsVisibles, LocalDate aujourdHui, ZoneId zone) {
        List<YearMonth> mois = Graphiques.derniersMois(aujourdHui, NOMBRE_MOIS);
        List<Long> total = new ArrayList<>();
        List<Long> enCours = new ArrayList<>();
        List<Long> termines = new ArrayList<>();
        for (YearMonth m : mois) {
            List<Projet> crees = projetsVisibles.stream()
                    .filter(p -> p.getDateCreation() != null
                            && !YearMonth.from(p.getDateCreation().atZone(zone)).isAfter(m))
                    .toList();
            total.add((long) crees.size());
            enCours.add(crees.stream().filter(p -> p.getStatut() == StatutProjet.EN_COURS).count());
            termines.add(crees.stream().filter(p -> p.getStatut() == StatutProjet.TERMINE
                    && p.getDateModification() != null
                    && !YearMonth.from(p.getDateModification().atZone(zone)).isAfter(m)).count());
        }
        return new GraphiqueSeries(mois.stream().map(Graphiques::libelleMois).toList(), List.of(
                new GraphiqueSeries.Serie("Total", total),
                new GraphiqueSeries.Serie("En cours", enCours),
                new GraphiqueSeries.Serie("Terminés", termines)));
    }

    /** Séries « Terminées », « En cours », « En retard » sur les 4 dernières semaines ISO. */
    private GraphiqueSeries tachesParSemaine(List<Activite> activitesVisibles, LocalDate aujourdHui, ZoneId zone) {
        List<String> libelles = new ArrayList<>();
        List<Long> terminees = new ArrayList<>();
        List<Long> enCours = new ArrayList<>();
        List<Long> enRetard = new ArrayList<>();
        LocalDate lundiCourant = aujourdHui.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = NOMBRE_SEMAINES - 1; i >= 0; i--) {
            LocalDate debut = lundiCourant.minusWeeks(i);
            LocalDate fin = debut.plusDays(6);
            Instant finInstant = fin.plusDays(1).atStartOfDay(zone).toInstant();
            libelles.add("Sem " + debut.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            terminees.add(activitesVisibles.stream().filter(a -> a.getDateFin() != null
                    && dansIntervalle(a.getDateFin().atZone(zone).toLocalDate(), debut, fin)).count());
            enCours.add(activitesVisibles.stream().filter(a -> a.getStatut() == StatutActivite.EN_COURS
                    && a.getDateCreation() != null && a.getDateCreation().isBefore(finInstant)).count());
            enRetard.add(activitesVisibles.stream().filter(a -> !a.estTerminee() && a.getDateEcheance() != null
                    && dansIntervalle(a.getDateEcheance(), debut, fin) && a.getDateEcheance().isBefore(aujourdHui))
                    .count());
        }
        return new GraphiqueSeries(libelles, List.of(
                new GraphiqueSeries.Serie("Terminées", terminees),
                new GraphiqueSeries.Serie("En cours", enCours),
                new GraphiqueSeries.Serie("En retard", enRetard)));
    }

    private static boolean dansIntervalle(LocalDate date, LocalDate debut, LocalDate fin) {
        return !date.isBefore(debut) && !date.isAfter(fin);
    }
}
