package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.StatutActivite;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Calcul des indicateurs d'un ensemble d'activités (logique pure, testable sans base).
 * <p>
 * Les catégories sont disjointes : terminées, en retard (non terminées dont
 * l'échéance est dépassée), en cours, à faire (à faire ou bloquées).
 *
 * @param total               nombre d'activités
 * @param terminees           activités au statut TERMINE
 * @param enCours             activités EN_COURS non en retard
 * @param aFaire              activités A_FAIRE non en retard
 * @param enRetard            activités en retard
 * @param tauxRealisation     terminees / total, en %, arrondi au dixième
 * @param tauxRetard          enRetard / total, en %, arrondi au dixième
 * @param dureeMoyenneJours   durée moyenne création → dernière modification des activités
 *                            terminées (approximation), {@code null} si aucune
 */
public record Indicateurs(
        long total,
        long terminees,
        long enCours,
        long aFaire,
        long enRetard,
        double tauxRealisation,
        double tauxRetard,
        Double dureeMoyenneJours) {

    public static Indicateurs calculer(Collection<Activite> activites, LocalDate aujourdHui) {
        long terminees = 0;
        long enCours = 0;
        long aFaire = 0;
        long enRetard = 0;
        double sommeJours = 0;
        long nombreDurees = 0;

        for (Activite activite : activites) {
            if (activite.estTerminee()) {
                terminees++;
                if (activite.dateFin() != null && activite.getDateCreation() != null) {
                    sommeJours += Duration.between(activite.getDateCreation(), activite.dateFin()).toMinutes() / 1440.0;
                    nombreDurees++;
                }
            } else if (activite.estEnRetard(aujourdHui)) {
                enRetard++;
            } else if (activite.getStatut() == StatutActivite.EN_COURS) {
                enCours++;
            } else {
                aFaire++;
            }
        }

        long total = activites.size();
        return new Indicateurs(total, terminees, enCours, aFaire, enRetard,
                pourcentage(terminees, total), pourcentage(enRetard, total),
                nombreDurees == 0 ? null : arrondir(sommeJours / nombreDurees));
    }

    static double pourcentage(long partie, long total) {
        return total == 0 ? 0.0 : arrondir(partie * 100.0 / total);
    }

    static double arrondir(double valeur) {
        return Math.round(valeur * 10.0) / 10.0;
    }
}
