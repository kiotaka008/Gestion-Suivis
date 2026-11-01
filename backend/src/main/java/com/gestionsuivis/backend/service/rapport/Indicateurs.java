package com.gestionsuivis.backend.service.rapport;

import com.gestionsuivis.backend.entity.Activite;
import com.gestionsuivis.backend.entity.StatutActivite;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Calcul des indicateurs d'un ensemble d'activités (logique pure, testable sans base).
 * <p>
 * Les catégories sont disjointes : terminées, en retard (non terminées dont
 * l'échéance est dépassée ou au statut EN_RETARD), en cours, à faire.
 *
 * @param total               nombre d'activités
 * @param terminees           activités au statut TERMINE
 * @param enCours             activités EN_COURS non en retard
 * @param aFaire              activités A_FAIRE non en retard
 * @param enRetard            activités en retard
 * @param tauxRealisation     terminees / total, en %, arrondi au dixième
 * @param tauxRetard          enRetard / total, en %, arrondi au dixième
 * @param heuresPrevues       somme des heures prévues renseignées
 * @param heuresReelles       somme des heures réelles renseignées
 * @param ecartHeures         heuresReelles - heuresPrevues (positif = dépassement)
 * @param tauxEcartHeures     ecartHeures / heuresPrevues en %, {@code null} sans heures prévues
 * @param dureeMoyenneJours   durée moyenne création → fin des activités terminées, {@code null} si aucune
 */
public record Indicateurs(
        long total,
        long terminees,
        long enCours,
        long aFaire,
        long enRetard,
        double tauxRealisation,
        double tauxRetard,
        BigDecimal heuresPrevues,
        BigDecimal heuresReelles,
        BigDecimal ecartHeures,
        Double tauxEcartHeures,
        Double dureeMoyenneJours) {

    public static Indicateurs calculer(Collection<Activite> activites, LocalDate aujourdHui) {
        long terminees = 0;
        long enCours = 0;
        long aFaire = 0;
        long enRetard = 0;
        BigDecimal prevues = BigDecimal.ZERO;
        BigDecimal reelles = BigDecimal.ZERO;
        double sommeJours = 0;
        long nombreDurees = 0;

        for (Activite activite : activites) {
            if (activite.estTerminee()) {
                terminees++;
                if (activite.getDateFin() != null && activite.getDateCreation() != null) {
                    sommeJours += Duration.between(activite.getDateCreation(), activite.getDateFin()).toMinutes() / 1440.0;
                    nombreDurees++;
                }
            } else if (activite.estEnRetard(aujourdHui)) {
                enRetard++;
            } else if (activite.getStatut() == StatutActivite.EN_COURS) {
                enCours++;
            } else {
                aFaire++;
            }
            if (activite.getHeuresPrevues() != null) {
                prevues = prevues.add(activite.getHeuresPrevues());
            }
            if (activite.getHeuresReelles() != null) {
                reelles = reelles.add(activite.getHeuresReelles());
            }
        }

        long total = activites.size();
        BigDecimal ecart = reelles.subtract(prevues);
        Double tauxEcart = prevues.signum() > 0
                ? arrondir(ecart.multiply(BigDecimal.valueOf(100)).divide(prevues, 4, RoundingMode.HALF_UP).doubleValue())
                : null;
        return new Indicateurs(total, terminees, enCours, aFaire, enRetard,
                pourcentage(terminees, total), pourcentage(enRetard, total),
                prevues.setScale(2, RoundingMode.HALF_UP), reelles.setScale(2, RoundingMode.HALF_UP),
                ecart.setScale(2, RoundingMode.HALF_UP), tauxEcart,
                nombreDurees == 0 ? null : arrondir(sommeJours / nombreDurees));
    }

    static double pourcentage(long partie, long total) {
        return total == 0 ? 0.0 : arrondir(partie * 100.0 / total);
    }

    static double arrondir(double valeur) {
        return Math.round(valeur * 10.0) / 10.0;
    }
}
