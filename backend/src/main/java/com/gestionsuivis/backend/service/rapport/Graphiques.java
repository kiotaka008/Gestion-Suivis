package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.dto.rapport.Repartition;
import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.Priorite;
import com.gestionsuivis.backend.entity.Projet;
import com.gestionsuivis.backend.entity.StatutProjet;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Construction des données de graphiques aux formats des données fictives du
 * frontend ({@code src/mocks/dashboardStats.ts} et {@code src/mocks/reports.ts}).
 */
public final class Graphiques {

    private static final String[] MOIS = {"Janv", "Févr", "Mar", "Avr", "Mai", "Juin",
            "Juil", "Août", "Sept", "Oct", "Nov", "Déc"};

    private Graphiques() {
    }

    public static String libelleMois(YearMonth mois) {
        return MOIS[mois.getMonthValue() - 1];
    }

    /** Les {@code nombre} derniers mois jusqu'au mois de {@code fin} inclus. */
    public static List<YearMonth> derniersMois(LocalDate fin, int nombre) {
        List<YearMonth> mois = new ArrayList<>();
        YearMonth dernier = YearMonth.from(fin);
        for (int i = nombre - 1; i >= 0; i--) {
            mois.add(dernier.minusMonths(i));
        }
        return mois;
    }

    /**
     * Répartition disjointe des projets : En cours, Terminés, En attente, En retard, Annulés
     * (un projet en retard n'est compté que dans « En retard »).
     */
    public static Repartition statutsProjets(Collection<Projet> projets, LocalDate aujourdHui) {
        long enCours = 0;
        long termines = 0;
        long enAttente = 0;
        long enRetard = 0;
        long annules = 0;
        for (Projet projet : projets) {
            if (projet.estEnRetard(aujourdHui)) {
                enRetard++;
            } else if (projet.getStatut() == StatutProjet.TERMINE) {
                termines++;
            } else if (projet.getStatut() == StatutProjet.EN_ATTENTE) {
                enAttente++;
            } else if (projet.getStatut() == StatutProjet.ANNULE) {
                annules++;
            } else {
                enCours++;
            }
        }
        return new Repartition(List.of("En cours", "Terminés", "En attente", "En retard", "Annulés"),
                List.of(enCours, termines, enAttente, enRetard, annules));
    }

    /** Répartition des activités par priorité : Urgent, Haute, Moyenne, Basse. */
    public static Repartition prioritesActivites(Collection<Activite> activites) {
        long[] compte = new long[4];
        for (Activite activite : activites) {
            Priorite priorite = activite.getPriorite() == null ? Priorite.MOYENNE : activite.getPriorite();
            switch (priorite) {
                case URGENTE -> compte[0]++;
                case HAUTE -> compte[1]++;
                case MOYENNE -> compte[2]++;
                case BASSE -> compte[3]++;
            }
        }
        return new Repartition(List.of("Urgent", "Haute", "Moyenne", "Basse"),
                List.of(compte[0], compte[1], compte[2], compte[3]));
    }
}
